package io.github.toyota32k.lib.player.model

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Size
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.utils.FlowableEvent
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtManualIncarnateResetableValue
import io.github.toyota32k.utils.android.RefBitmap.Companion.toRef
import io.github.toyota32k.utils.android.RefBitmapFlow
import io.github.toyota32k.utils.lifecycle.disposableObserve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration

@OptIn(UnstableApi::class)
open class BasicPlayerModel(
    context: Context,
    coroutineScope: CoroutineScope,
    initialAutoPlay:Boolean,
    override val continuousPlay:Boolean,
    private val photoSlideShowModel: PhotoSlideShowModelImpl = PhotoSlideShowModelImpl()
) : IPlayerModel, IUtPropOwner, IPhotoSlideShowModel by photoSlideShowModel {
    companion object {
        val logger by lazy { UtLog("PM", TpLib.logger) }
    }

    // region Properties / Status
    private var autoPlay:Boolean = initialAutoPlay

    final override val context: Application = context.applicationContext as Application           // ApplicationContextならViewModelが持っていても大丈夫だと思う。

    /**
     * エラーメッセージ
     */
    final override val errorMessage: StateFlow<String?> = MutableStateFlow(null)

    enum class PlayerState {
        None,       // 初期状態
        Loading,
        Ready,
        Error,
    }
    protected val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState.None)
    protected val ended = MutableStateFlow(false)                   // 次回再生開始時に先頭に戻すため、最後まで再生したことを覚えておくフラグ
    private val watchPositionEvent = FlowableEvent(initial = false, autoReset = false)    // スライダー位置監視を止めたり、再開したりするためのイベント

    // ExoPlayerのリスナー
    private val listener =  PlayerListener()

    // PlayerNotificationManager
    private var playerNotificationManager: PlayerNotificationManager? = null

    // Volume Control
    final override val volume = MutableStateFlow(1f)
    final override val mute = MutableStateFlow(false)

//    private val resetables = ManualResetables()
    private val resetablePlayer = UtManualIncarnateResetableValue(
        onIncarnate = {
            ExoPlayer.Builder(context).build().apply {
                addListener(listener)
                playerNotificationManager?.setPlayer(this)
                this@BasicPlayerModel.volume.value = this.volume
                this@BasicPlayerModel.mute.value = false
            }
        },
        onReset = { player->
            playerNotificationManager?.setPlayer(null)
            player.removeListener(listener)
            player.release()
        }
    )


    final override val scope = CoroutineScope( coroutineScope.coroutineContext + SupervisorJob() )
    private val isVideoPlaying = MutableStateFlow(false)
    private val isPhotoPlaying = MutableStateFlow(false)
    final override val isPlaying: StateFlow<Boolean> = combine(isVideoPlaying, isPhotoPlaying) { v, p -> v || p }.stateIn(scope, SharingStarted.Eagerly, false)
    final override val isLoading = state.map { it == PlayerState.Loading }.stateIn(scope, SharingStarted.Eagerly, false)
    final override val isReady = state.map { it== PlayerState.Ready }.stateIn(scope, SharingStarted.Eagerly, false)
    final override val isError = errorMessage.map { !it.isNullOrBlank() }.stateIn(scope, SharingStarted.Lazily, false)

    // ExoPlayer
    private val isDisposed:Boolean get() = !resetablePlayer.hasValue      // close済みフラグ
    protected val player: ExoPlayer? get() = if(resetablePlayer.hasValue) resetablePlayer.value else null

    protected inline fun withPlayer(fn:(ExoPlayer)->Unit) {
        player?.apply{ fn(this) } ?: logger.error("no exoPlayer now")
    }
    protected inline fun <T> runOnPlayer(def:T, fn:ExoPlayer.()->T):T {
        return player?.run {
            fn()
        } ?: def
    }
    protected inline fun runOnPlayer(fn:ExoPlayer.()->Unit) {
        player?.apply {
            fn()
        }
    }

    private var _frameDuration:Long = 0L
    private val frameDuration:Long get() {
        if(_frameDuration==0L) {
            val rate = runOnPlayer(0L) { videoFormat?.frameRate?.toLong() ?: 24L }
            _frameDuration = if(rate>10) 1000L/rate else 100L
        }
        return _frameDuration
    }

    /**
     * 現在再生中の動画のソース
     */
    override val currentSource:StateFlow<IMediaSource?> = MutableStateFlow(null)
    override val currentSourceType: StateFlow<String?> = currentSource.map { it?.type?.lowercase() }.stateIn(scope, SharingStarted.Lazily,null)
    override val isCurrentSourcePhoto: StateFlow<Boolean> = currentSourceType.map {
        when (it) {
            "jpg", "jpeg", "png", "gif" -> true
            else -> false
        }
    }.stateIn(scope, SharingStarted.Lazily, false)
    override val isCurrentSourceVideo: StateFlow<Boolean> = currentSourceType.map { it == "mp4" }.stateIn(scope, SharingStarted.Lazily, false)

    /**
     * 動画の全再生時間
     */
    override val naturalDuration: StateFlow<Long> = MutableStateFlow(0L)

    /**
     * 動画の画面サイズ情報
     * ExoPlayerの動画読み込みが成功したとき onVideoSizeChanged()イベントから設定される。
     */
    override val videoSize: StateFlow<Size?> = MutableStateFlow(null)

    /**
     * 再生範囲（部分再生用）
     */
    override val playRange: StateFlow<Range?> = MutableStateFlow(null)

    /**
     * （外部から）エラーメッセージを設定する
     */
    @Suppress("unused")
    fun setErrorMessage(msg:String?) {
        errorMessage.mutable.value = msg
    }

    // endregion

    // region Operation

    /**
     * 回転
     */
    final override val rotation = MutableStateFlow(0)
    override fun rotate(value: Rotation) {
        if(value == Rotation.NONE) {
            rotation.value = 0
        } else {
            rotation.value = Rotation.normalize(rotation.value + value.degree)
        }
    }

    // endregion

    // region Initialize / Termination

    init {
        isVideoPlaying.onEach {
            if (it) {
                watchPositionEvent.set()
            }
        }.launchIn(scope)

        ended.onEach {
            if (it) {
                onPlaybackCompleted()
            }
        }.launchIn(scope)

        combine(volume,mute) { volume, mute ->
            if(!mute) volume else 0f
        }.onEach { v ->
            player?.volume = v
        }.launchIn(scope)

        scope.launch {
            while (!isDisposed) {
                watchPositionEvent.waitOne()
                if (isVideoPlaying.value) {
                    withPlayer { player ->
                        val src = currentSource.value
                        val pos = player.currentPosition
                        playerSeekPosition.mutable.value = pos
                        // 部分再生のチェック
                        val range = playRange.value?.takeIf { it.isTerminated }
                        val endPosition = range?.end ?: naturalDuration.value
                        var adjustedPosition = range.clamp(pos)

                        if (src is IMediaSourceWithChapter) {
                            val chapterList = src.getChapterList()
                            if (chapterList.isNotEmpty) {
                                // 無効区間、トリミングによる再生スキップの処理
                                val dr = chapterList.disabledRanges(src.trimming)
                                val hit = dr.firstOrNull { it.contains(adjustedPosition) }
                                if (hit != null) {
                                    adjustedPosition = if (hit.end == 0L || hit.end >= endPosition) {
                                        endPosition
                                    } else {
                                        range.clamp(hit.end)
                                    }
                                }
                            }
                        }
                        if (adjustedPosition != pos) {
                            if(adjustedPosition>=endPosition) {
                                ended.value = true
                            }
                            seekTo(adjustedPosition)
                        }
                    }
                } else {
                    watchPositionEvent.reset()
                }
                delay(50)
            }
        }
    }

    override fun setPlayRange(range: Range?) {
        if(range==null) {
            playRange.mutable.value = null
        } else {
            if(naturalDuration.value>0) {
                playRange.mutable.value = Range.terminate(range, naturalDuration.value)
                val cur = currentPosition
                val pos = range.clamp(cur)
                if(pos != cur) {
                    if(pos == range.end) {
                        seekTo(range.start)
                    } else {
                        seekTo(pos)
                    }
                }
            } else {
                playRange.mutable.value = range
            }
        }
    }

    /**
     * 解放
     */
    override fun close() {
        logger.debug()
        currentSource.mutable.value = null
        resetablePlayer.reset()
        scope.cancel()
    }

    override fun killPlayer() {
        logger.debug()
        resetablePlayer.reset()
    }

    override fun revivePlayer():Boolean {
        logger.debug()
        return resetablePlayer.incarnate()
    }
    // endregion

    // region Seeking

    inner class SeekManagerEx : ISeekManager {
        override val requestedPositionFromSlider = MutableStateFlow(-1L)
        private var lastOperationTick:Long = 0L
        private var fastSync = false
        private var running = false
        init {
            run()
        }

        private fun run() {
            if(running) return
            running = true
            requestedPositionFromSlider.onEach {
                val tick = System.currentTimeMillis()
                if(0<=it && it<=naturalDuration.value) {
                    if(tick-lastOperationTick<500) {
                        setFastSeek()
                    } else {
                        setExactSync()
                    }
                    clippingSeekTo(it, false)
                }
                delay(200L)
            }.onCompletion {
                logger.debug("SeekManager stopped.")
                running = false
            }.launchIn(scope)
        }

        private fun setFastSeek() {
            if(!fastSync) {
                runOnPlayer {
                    setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    fastSync = true
                }
            }
        }
        private fun setExactSync() {
            if(fastSync) {
                runOnPlayer {
                    setSeekParameters(SeekParameters.EXACT)
                    fastSync = false
                }
            }
        }
        fun reset() {
            setExactSync()
            lastOperationTick = 0L
            requestedPositionFromSlider.value = -1L
        }
    }
    override val seekManager = SeekManagerEx()

    /**
     * 0-durationで　引数 pos をクリップして返す。
     */
    protected fun clipPosition(pos:Long, trimming: Range?):Long {
        val duration = naturalDuration.value
        val s:Long
        val e:Long
        if(trimming==null) {
            s = 0L
            e = duration
        } else {
            s = max(0, trimming.start)
            e = if(trimming.end in (s + 1) until duration) trimming.end else duration
        }
        return playRange.value.clamp(pos.coerceIn(s,e))
    }

    /**
     * pseudoClippingを考慮したシーク
     */
    protected fun clippingSeekTo(pos:Long, awareTrimming:Boolean) {
        withPlayer { player ->
            val clippedPos = clipPosition(pos, if (awareTrimming) currentSource.value?.trimming else null)
            player.seekTo(clippedPos)
            playerSeekPosition.mutable.value = player.currentPosition
        }
    }

    override fun seekRelative(seek:Long) {
        if(!isReady.value) return
        withPlayer { player ->
            clippingSeekTo(player.currentPosition + seek, true)
        }
    }

    override fun seekRelativeByFrame(frameCount: Long) {
        if(!isReady.value) return
        val seek = frameCount * frameDuration
        withPlayer { player->
            clippingSeekTo(player.currentPosition + seek, true)
        }
    }

    override fun seekTo(seek:Long) {
        if(!isReady.value) return
        clippingSeekTo(seek, true)
    }

    /**
     * プレーヤー内の再生位置
     * 動画再生中は、タイマーで再生位置(player.currentPosition)を監視して、このFlowにセットする。
     * スライダーは、これをcollectして、シーク位置を同期する。
     */
    override val playerSeekPosition: StateFlow<Long> =  MutableStateFlow(0L)

    /**
     * PlaylistPlayerModel は、BasicPlayerModelインスタンスに委譲することによって IPlayerModel を実装しているため、
     * init で、 ended を subscribeした、onPlaybackCompleted では、 PlaylistPlayerModel#onPlaybackCompleted が呼ばれない。
     * 苦肉の策で、デリゲートを設定できるようにした。
     */
    var onPlaybackCompletedHandler: (() -> Boolean)? = null
    /**
     * 再生中に EOS に達したときの処理
     * デフォルト： 再生を止めて先頭にシークする
     */
    override fun onPlaybackCompleted() {
        if(onPlaybackCompletedHandler?.invoke()==true) {
            return
        }
        pause()
        clippingSeekTo(0, true)
    }

    // endregion

    // region ExoPlayer Video Player

    /**
     * ExoPlayerのイベントリスナークラス
     */
    inner class PlayerListener :  Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            this@BasicPlayerModel.videoSize.mutable.value = Size(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.stackTrace(error)
            if(!isReady.value) {
                state.mutable.value = PlayerState.Error
                errorMessage.mutable.value = context.getString(R.string.video_player_error)
            } else {
                logger.warn("ignoring exo error.")
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            isVideoPlaying.value = playWhenReady
        }

        override fun onPlaybackStateChanged(playbackState:Int) {
            super.onPlaybackStateChanged(playbackState)
            when(playbackState) {
                Player.STATE_IDLE -> {
                    state.mutable.value = PlayerState.None
                }
                Player.STATE_BUFFERING -> {
                    if(state.value == PlayerState.None) {
                        state.mutable.value = PlayerState.Loading
                    }
                }
                Player.STATE_READY ->  {
                    ended.value = false
                    state.mutable.value = PlayerState.Ready
                    naturalDuration.mutable.value = runOnPlayer(0L) {
                        duration.coerceAtLeast(0L)
                    }
                    playRange.value?.apply {
                        if(!isTerminated) {
                            setPlayRange(this)
                        }
                    }

                }
                Player.STATE_ENDED -> {
                    ended.value = true
                }
                else -> {}
            }
        }
    }

    override val currentPosition: Long
        get() = runOnPlayer(0L) { currentPosition }


    /**
     * View （PlayerView）に Playerを関連付ける
     */
    override fun associatePlayerView(playerView: PlayerView) {
        withPlayer { player ->
            playerView.player = player
        }
    }

    override fun dissociatePlayerView(playerView: PlayerView) {
        playerView.player = null
    }

    /**
     * バックグラウンド再生（PlayerNotificationManager）対応用
     */
    override fun associateNotificationManager(manager: PlayerNotificationManager) {
        playerNotificationManager = manager
        withPlayer { player ->
            manager.setPlayer(player)
        }
    }

    // endregion

    // region Handling Media Sources

    @Suppress("unused")
    fun MediaItem.getAmvSource(): IMediaSource {
        return this.localConfiguration!!.tag as IMediaSource
    }

    fun makeMediaSource(item:IMediaSource) : MediaSource {
        return ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(MediaItem.Builder().setUri(item.uri).setTag(item).build())
    }

    override fun setSource(src:IMediaSource?) {
        reset()
        if (src == null) return
        naturalDuration.mutable.value = 0L
        currentSource.mutable.value = src
        setPlayRange(null)

        val isPhoto = src.isPhoto
        val pos = if (isPhoto) {
            ended.value = false
            0
        } else {
            max(src.trimming.start, src.startPosition.getAndSet(0L))
        }

        runOnPlayer {
            _frameDuration = 0L // 次回必要になれば再取得される
            if (!isPhoto) {
                setMediaSource(makeMediaSource(src), pos)
                prepare()
            } else {
                stop()
                clearMediaItems()
            }
        }
        if (autoPlay) {
            play()
        }
    }

    // endregion

    // region Controlling Player

    /**
     * 再初期化
     */
    override fun reset() {
        logger.debug()
        pause()
        currentSource.mutable.value = null
        playRange.mutable.value = null
        seekManager.reset()
        playerSeekPosition.mutable.value = 0L
        errorMessage.mutable.value = null
    }

    /**
     * Play / Pauseをトグル
     */
    override fun togglePlay() {
        if(isDisposed) return
        if(runOnPlayer(false) { playWhenReady} ) {
            stop()
        } else {
            play()
        }
    }

    /**
     * （再生中でなければ）再生を開始する
     */
    override fun play() {
        logger.debug()
        if(isDisposed) return
        autoPlay = true
        errorMessage.mutable.value = null
        val item = currentSource.value ?: return
        if (!item.isPhoto) {
            runOnPlayer { playWhenReady = true }
        } else if (isPhotoSlideShowEnabled) {
            isPhotoPlaying.value = true
            CoroutineScope(Dispatchers.Main).launch {
                delay(photoSlideShowDuration)
                if (item == currentSource.value && isPhotoPlaying.value) {
                    ended.value = true
                }
            }
        }
    }

    /**
     * 再生を中断する
     */
    override fun pause() {
        logger.debug()
        if(isDisposed) return
        isPhotoPlaying.value = false
        runOnPlayer { playWhenReady = false }
    }

    override fun stop() {
        autoPlay = false
        pause()
    }

    // endregion

    // region PhotoViewer + SlideShow

    override fun enablePhotoViewer(duration: Duration) {
        photoSlideShowModel.enablePhotoViewer(duration)
    }

    override val shownBitmap: RefBitmapFlow = RefBitmapFlow()

    @SuppressLint("CheckResult")
    override fun attachPhotoView(photoView: ImageView): IDisposable {
        return currentSource.disposableObserve {
            if(it?.isPhoto == true) {
//                if (it.uri.startsWith("content:")) {
//                    val bytes = context.contentResolver.openInputStream(it.uri.toUri())?.use { it.readBytes() } ?: return@disposableObserve
//                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//                    logger.info("Photo: bytes=${bytes.size}")
//                    shownBitmap.mutable.value = bitmap
//                    videoSize.mutable.value = Size(bitmap.width, bitmap.height)
//                    state.mutable.value = PlayerState.Ready
//                    return@disposableObserve
//                }
                CoroutineScope(Dispatchers.Main).launch {
                    state.mutable.value = PlayerState.Loading
                    Glide.with(photoView)
                        .apply {
                            if (it.type == "gif") {
                                asGif()
                            }
                        }
                        .load(it.uri)
                        .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: com.bumptech.glide.request.target.Target<Drawable?>,
                                isFirstResource: Boolean
                            ): Boolean {
                                logger.error("Failed to load image: ${e?.message}")
                                state.mutable.value = PlayerState.Error
                                return false
                            }
                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: com.bumptech.glide.request.target.Target<Drawable?>?,
                                dataSource: com.bumptech.glide.load.DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                // ロードが成功した場合の処理
                                val width = resource.intrinsicWidth
                                val height = resource.intrinsicHeight
                                videoSize.mutable.value = Size(width, height)
                                state.mutable.value = PlayerState.Ready
                                shownBitmap.value = resource.toBitmap().toRef().apply {
                                    // Glide が Bitmap を保持しているので外部でrecycleすると状態異常を起こす
                                    // これを防ぐため、Glideで作成したBitmapは、参照カウンタを常に１以上に保つ
                                    addRef()
                                }
                                return false // falseを返すと、Glideが通常通りImageViewに画像を表示します
                            }
                        })
                        .into(photoView)
                }
            } else {
                photoView.setImageBitmap(null)
                shownBitmap.value = null
            }

        }
    }

    // endregion
}