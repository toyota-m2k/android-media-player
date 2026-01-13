package io.github.toyota32k.lib.player.model

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.TpFrameExtractor
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.common.formatTimeMs
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.android.RefBitmap
import io.github.toyota32k.utils.android.RefBitmap.Companion.toRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.Closeable
import java.lang.ref.WeakReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * コントローラーの共通実装
 * ControlPanelModel              基本実装（AmvFrameSelectorViewで使用）
 *   + FullControlPanelModel      フル機能プレイヤー（AmvPlayerUnitView）用
 *   + TrimmingControlPanelModel  トリミング画面(AMvTrimmingPlayerView)用
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
open class PlayerControllerModel(
    val playerModel: IPlayerModel,
    val supportFullscreen:Boolean,
    val supportPinP:Boolean,
    val snapshotHandler:((Long,RefBitmap)->Unit)?,
    val enableRotateRight:Boolean,
    val enableRotateLeft:Boolean,
    val showNextPreviousButton:Boolean,
    val enableSliderLock: Boolean,
    val initialEnableSliderLock: Boolean,
    var enableVolumeController:Boolean,
    val seekSmall:RelativeSeek?,
    val seekMedium:RelativeSeek?,
    val seekLarge:RelativeSeek?,
    val counterInMs:Boolean,
    val snapshotSourceSelectable: Boolean,
    var snapshotSource: SnapshotSource,
    val magnifySliderHandler:(suspend (RangedPlayModel?,duration:Long)->RangedPlayModel?)?
) : Closeable, IUtPropOwner {
    companion object {
        val logger by lazy { UtLog("CPM", TpLib.logger) }
    }
    data class RelativeSeek(val backward:Long, val forward:Long)

    enum class SnapshotSource(@param:StringRes val resId:Int) {
        // ExoPlayerのSurfaceView/TextureViewからキャプチャする。
        // FRAME_EXTRACTOR より正確な位置の画像が得られるし高速。
        CAPTURE_PLAYER(R.string.snapshot_capture_player_screen),
        // MediaMetadataRetrieverを使って、指定位置のフレームを抽出する。
        // 従来は、この方法しかなかったが、（HDRモードの動画で？）再生位置のズレが無視できないレベルになったので
        // 今後は、CAPTURE_PLAYERをデフォルトにする。
        FRAME_EXTRACTOR(R.string.snapshot_extract_frame),
    }

    class Builder(val context:Context, private val coroutineScope: CoroutineScope) {
        private var mSupportChapter:Boolean = false
        private var mPlaylist:IMediaFeed? = null
        private var mAutoPlay:Boolean = false
        private var mContinuousPlay:Boolean = false
        private var mSupportFullscreen:Boolean = false
        private var mSupportPinP:Boolean = false
        private var mSnapshotHandler:((Long,RefBitmap)->Unit)? = null
        private var mEnableRotateRight:Boolean = false
        private var mEnableRotateLeft:Boolean = false
        private var mShowNextPreviousButton:Boolean = false
        private var mEnableSliderLock:Boolean = false
        private var mInitialEnableSliderLock:Boolean = false
        private var mSeekSmall:RelativeSeek? = null
        private var mSeekMedium:RelativeSeek? = null
        private var mSeekLarge:RelativeSeek? = null
        private var mCounterInMs:Boolean = false
        private var mEnableVolumeController:Boolean = false
        private var mEnablePhotoViewer:Boolean = false
        private var mPhotoSlideShowDuration: Duration = 5.seconds
        private var mCustomPhotoLoader: IPhotoLoader? = null
        private var mHideChapterViewIfEmpty = false
        private var mSnapshotSource: SnapshotSource = SnapshotSource.CAPTURE_PLAYER
        private var mSnapshotSourceSelectable: Boolean = true
        private var mMagnifySliderHandler:(suspend (RangedPlayModel?, duration:Long)->RangedPlayModel?)? = null

        fun supportChapter(hideChapterViewIfEmpty:Boolean=false):Builder {
            mSupportChapter = true
            mHideChapterViewIfEmpty = hideChapterViewIfEmpty
            return this
        }
        fun supportPlaylist(playlist:IMediaFeed, autoPlay:Boolean, continuousPlay:Boolean):Builder {
            mPlaylist = playlist
            mAutoPlay = autoPlay
            mContinuousPlay = continuousPlay
            return this
        }
        fun supportFullscreen():Builder {
            mSupportFullscreen = true
            return this
        }
        fun supportPiP():Builder {
            mSupportPinP = true
            return this
        }

        fun supportSnapshot(snapshotHandler:(Long,RefBitmap)->Unit):Builder {
            mSnapshotHandler = snapshotHandler
            return this
        }

        /**
         * シークスライダーの拡大をサポートする
         */
        fun supportMagnifySlider(handler:suspend (RangedPlayModel?, duration:Long)->RangedPlayModel?) = apply {
            mMagnifySliderHandler = handler
        }

        fun showNextPreviousButton():Builder {
            mShowNextPreviousButton = true
            return this
        }

        fun enableRotateRight():Builder {
            mEnableRotateRight = true
            return this
        }
        fun enableRotateLeft():Builder {
            mEnableRotateLeft = true
            return this
        }
        fun enableSeekSmall(backward:Long, forward:Long):Builder {
            mSeekSmall = RelativeSeek(backward, forward)
            return this
        }
        fun enableSeekMedium(backward:Long, forward:Long):Builder {
            mSeekMedium = RelativeSeek(backward, forward)
            return this
        }
        fun enableSeekLarge(backward:Long, forward:Long):Builder {
            mSeekLarge = RelativeSeek(backward, forward)
            return this
        }

        fun enableSliderLock(initial:Boolean):Builder {
            mEnableSliderLock = true
            mInitialEnableSliderLock = initial
            return this
        }
        fun enableVolumeController(enable:Boolean):Builder {
            mEnableVolumeController = enable
            return this
        }
        fun counterInMs(sw:Boolean=true):Builder {
            mCounterInMs = sw
            return this
        }
        fun enablePhotoViewer(slideDuration:Duration=Duration.INFINITE):Builder {
            mEnablePhotoViewer = true
            mPhotoSlideShowDuration = slideDuration
            return this
        }
        fun disablePhotoViewer():Builder {
            mEnablePhotoViewer = false
            return this
        }
        @Deprecated("use IPhotoLoader")
        fun customPhotoLoader(loader:suspend (IMediaSource)-> RefBitmap?):Builder = apply {
            mCustomPhotoLoader = object: IPhotoLoader {
                override suspend fun loadBitmap(src: IMediaSource): IBitmapInfo? {
                    val bitmap = loader(src) ?: return BitmapInfo.useGlide
                    return BitmapInfo.withBitmap(bitmap)
                }
            }
        }
        fun customPhotoLoader(loader: IPhotoLoader):Builder = apply {
            mCustomPhotoLoader = loader
        }
        fun autoPlay(autoPlay:Boolean):Builder {
            mAutoPlay = autoPlay
            return this
        }
        fun continuousPlay(continuousPlay:Boolean):Builder {
            mContinuousPlay = continuousPlay
            return this
        }

        /**
         * Snapshotの取得方法
         *
         * @param source    CAPTURE_PLAYER: ExoPlayerのSurfaceView/TextureViewからキャプチャする。一瞬リサイズが発生するため若干画面がチラつく。
         *                  FRAME_EXTRACTOR: MediaMetadataRetrieverを使って、指定位置のフレームを抽出する。表示とのズレが生じる。
         * @param selectable カメラボタン長押しで、ポップアップメニューを表示して、SnapshotSourceの切り替えをサポートする場合は true
         */
        fun snapshotSource(source:SnapshotSource, selectable:Boolean=true):Builder {
            mSnapshotSource = source
            mSnapshotSourceSelectable = selectable
            return this
        }

        @OptIn(UnstableApi::class)
        fun build():PlayerControllerModel {
            val playerModel = when {
                mSupportChapter && mPlaylist!=null -> PlaylistChapterPlayerModel(context, coroutineScope, mPlaylist!!, mAutoPlay, mContinuousPlay, mCustomPhotoLoader, mHideChapterViewIfEmpty)
                mSupportChapter -> ChapterPlayerModel(context, coroutineScope, mHideChapterViewIfEmpty, mAutoPlay, mCustomPhotoLoader)
                mPlaylist!=null -> PlaylistPlayerModel(context, coroutineScope, mPlaylist!!, mAutoPlay, mContinuousPlay, mCustomPhotoLoader)
                else -> BasicPlayerModel(context, coroutineScope, mAutoPlay, false, mCustomPhotoLoader)
            }
            if (mEnablePhotoViewer) {
                playerModel.enablePhotoViewer(true)
                playerModel.photoSlideShowDuration = mPhotoSlideShowDuration
            }
            return PlayerControllerModel(
                playerModel,
                supportFullscreen = mSupportFullscreen,
                supportPinP = mSupportPinP,
                snapshotHandler = mSnapshotHandler,
                enableRotateRight = mEnableRotateRight,
                enableRotateLeft = mEnableRotateLeft,
                showNextPreviousButton = mShowNextPreviousButton,
                enableSliderLock = mEnableSliderLock,
                initialEnableSliderLock = mInitialEnableSliderLock,
                enableVolumeController = mEnableVolumeController,
                seekSmall = mSeekSmall,
                seekMedium = mSeekMedium,
                seekLarge = mSeekLarge,
                counterInMs = mCounterInMs,
                snapshotSource = mSnapshotSource,
                snapshotSourceSelectable = mSnapshotSourceSelectable,
                magnifySliderHandler = mMagnifySliderHandler
            )
        }
    }


    /**
     * ApplicationContext参照用
     */
    val context: Context get() = playerModel.context

    /**
     * AmvExoVideoPlayerのbindViewModelで、playerをplayerView.playerに設定するか？
     * 通常は true。ただし、FullControlPanelのように、PinP/FullScreenモードに対応する場合は、
     * どのビューに関連付けるかを個別に分岐するため、falseにする。
     */
    open val autoAssociatePlayer:Boolean = true

    val showControlPanel = MutableStateFlow(true)
    val rangePlayModel:StateFlow<RangedPlayModel?> = MutableStateFlow(null)
    val hasNextRange = MutableStateFlow(false)
    val hasPrevRange = MutableStateFlow(false)
    val volume: MutableStateFlow<Float> get() = playerModel.volume  // 0-100
    val mute: MutableStateFlow<Boolean> get() = playerModel.mute
    val commandVolume = LiteUnitCommand()
    val commandChangeRange = LiteCommand(::changeRange)
//    val isCurrentSourcePhoto: Flow<Boolean> = playerModel.currentSource.map { playerModel.isPhotoViewerEnabled && it?.isPhoto==true }

    interface IScreenshotSource {
        suspend fun takeScreenshot():Bitmap?
    }

    private var exoPlayerSnapshotSourceRef: WeakReference<IScreenshotSource>? = null
    var exoPlayerSnapshotSource
        get() = exoPlayerSnapshotSourceRef?.get()
        set(v) {
            exoPlayerSnapshotSourceRef = if(v!=null) WeakReference(v) else null
        }

    fun setRangePlayModel(rvm:RangedPlayModel?) {
        rvm?.initializeRangeContainsPosition(playerModel.currentPosition)
        rangePlayModel.mutable.value = rvm
        playerModel.setPlayRange(rvm?.currentRange)
        if(rvm!=null) {
            hasNextRange.mutable.value = rvm.hasNext
            hasPrevRange.mutable.value = rvm.hasPrevious
        }
    }

    private fun changeRange(next:Boolean) {
        val rvm = rangePlayModel.value ?: return
        val range = if(next) {
            rvm.next()
        } else {
            rvm.previous()
        }
        if(range!=null) {
            playerModel.setPlayRange(range)
        }
        hasNextRange.mutable.value = rvm.hasNext
        hasPrevRange.mutable.value = rvm.hasPrevious
    }

    private fun seekRelative(forward:Boolean, s:RelativeSeek?) {
        if(s==null) return
        if(forward) {
            if(s.forward>0L) {
                playerModel.seekRelative(s.forward)
            } else {
                playerModel.seekRelativeByFrame(1)
            }
        } else {
            if(s.backward>0L) {
                playerModel.seekRelative(-s.backward)
            } else {
                playerModel.seekRelativeByFrame(-1)
            }
        }
    }

    private val _lockSliderFlow = MutableStateFlow(initialEnableSliderLock)
    val lockSlider = _lockSliderFlow.map {enableSliderLock && it}
    val commandPlay = LiteUnitCommand(playerModel::play)
    val commandPause = LiteUnitCommand(playerModel::stop)
    val commandSeekLarge = LiteCommand<Boolean> { seekRelative(it, seekLarge) }
    val commandSeekMedium = LiteCommand<Boolean> { seekRelative(it, seekMedium) }
    val commandSeekSmall = LiteCommand<Boolean> { seekRelative(it, seekSmall) }
    val commandFullscreen = LiteUnitCommand { setWindowMode(WindowMode.FULLSCREEN) }
    val commandPinP = LiteUnitCommand { setWindowMode(WindowMode.PINP) }
    val commandCollapse = LiteUnitCommand { setWindowMode(WindowMode.NORMAL) }
    val commandSnapshot = LiteUnitCommand(::snapshot)
    val commandRotate = LiteCommand<Rotation> { playerModel.rotate(it) }
    val commandLockSlider = LiteUnitCommand { _lockSliderFlow.value = !_lockSliderFlow.value }

    // endregion

    // region Fullscreen/PinP

    enum class WindowMode {
        NORMAL,
        FULLSCREEN,
        PINP
    }
    val windowMode : StateFlow<WindowMode> = MutableStateFlow(WindowMode.NORMAL)
    fun setWindowMode(mode:WindowMode) {
        logger.debug("mode=${windowMode.value} --> $mode")
        windowMode.mutable.value = mode
    }

    // endregion

    // region Snapshot

    fun permitSnapshot(permit:Boolean) {
        permitSnapshot.mutable.value = permit
    }
    val permitSnapshot: StateFlow<Boolean> = MutableStateFlow(true)
    val takingSnapshot: StateFlow<Boolean> = MutableStateFlow(false)

    private fun bitmapFromPhoto(src:IMediaSource): RefBitmap? {
        if (!src.isPhoto) return null
        if (playerModel.currentSource.value != src) return null
        return playerModel.shownBitmap.value
//        return withContext(Dispatchers.IO) {
//            Glide.with(context)
//                .asBitmap() // Bitmapとしてロードすることを明示
//                .load(src.uri)
//                .submit()
//                .get()
//        }
    }
    private suspend fun bitmapFromExoPlayer():Bitmap? {
        return exoPlayerSnapshotSource?.takeScreenshot()
    }
    private suspend fun bitmapFromFrameExtractor(src:IMediaSource, position:Long):Bitmap? {
        if (src.isPhoto) return null
        return TpFrameExtractor.create(playerModel.context, src.uri).use { extractor ->
            extractor.extractFrame(position)
        }
    }
    private suspend fun bitmapFromSrc(src:IMediaSource, position:Long, angle:Int):RefBitmap? {
        return if(src.isPhoto) {
            bitmapFromPhoto(src)?.rotate(angle.toFloat())
        } else {
            when(snapshotSource) {
                SnapshotSource.CAPTURE_PLAYER -> bitmapFromExoPlayer()
                SnapshotSource.FRAME_EXTRACTOR -> bitmapFromFrameExtractor(src, position)
            }?.toRef()?.rotate(angle.toFloat())
        }
    }

    private fun snapshot() {
        val handler = snapshotHandler ?: return
        val src = playerModel.currentSource.value ?: return
        playerModel.pause()
        takingSnapshot.mutable.value = true
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val pos: Long = if (src.isPhoto) 0L else playerModel.currentPosition
                val angle = Rotation.normalize(playerModel.rotation.value)
                val bitmap = bitmapFromSrc(src, pos, angle)
                if (bitmap != null) {
                    handler(pos, bitmap)
                }
            } finally {
                takingSnapshot.mutable.value = false
            }
        }
    }

    // endregion

    // region Slider

    /**
     * スライダーのカウンター表示文字列
     */
    val fullCounterText:Flow<String> = combine(playerModel.playerSeekPosition, playerModel.naturalDuration) { pos, duration->
        if(!counterInMs) "${formatTime(pos,duration)} / ${formatTime(duration,duration)}" else "${formatTimeMs(pos,duration)} / ${formatTimeMs(duration,duration)}"
    }
    val counterText:Flow<String> = combine(playerModel.playerSeekPosition, playerModel.naturalDuration) { pos, duration->
        if(!counterInMs) formatTime(pos,duration) else formatTimeMs(pos,duration)
    }

    // endregion

    override fun close() {
        playerModel.close()
    }
}
