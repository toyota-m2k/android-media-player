package io.github.toyota32k.lib.player.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.TpFrameExtractor
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.common.formatTimeMs
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.utils.IUtPropOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.time.Duration

/**
 * コントローラーの共通実装
 * ControlPanelModel              基本実装（AmvFrameSelectorViewで使用）
 *   + FullControlPanelModel      フル機能プレイヤー（AmvPlayerUnitView）用
 *   + TrimmingControlPanelModel  トリミング画面(AMvTrimmingPlayerView)用
 */
@Suppress("unused")
open class PlayerControllerModel(
    val playerModel: IPlayerModel,
    val supportFullscreen:Boolean,
    val supportPinP:Boolean,
    val snapshotHandler:((Long,Bitmap)->Unit)?,
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
) : Closeable, IUtPropOwner {
    companion object {
        val logger by lazy { UtLog("CPM", TpLib.logger) }
    }
    data class RelativeSeek(val backward:Long, val forward:Long)

    class Builder(val context:Context, val coroutineScope: CoroutineScope) {
        private var mSupportChapter:Boolean = false
        private var mPlaylist:IMediaFeed? = null
        private var mAutoPlay:Boolean = false
        private var mContinuousPlay:Boolean = false
        private var mSupportFullscreen:Boolean = false
        private var mSupportPinP:Boolean = false
        private var mSnapshotHandler:((Long,Bitmap)->Unit)? = null
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
        private var mPhotoSlideShowDuration: Duration? = null
        private var mPhotoResolver: (suspend (item:IMediaSource)->Bitmap?)? = null

        private var mHideChapterViewIfEmpty = false

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

        fun supportSnapshot(snapshotHandler:(Long,Bitmap)->Unit):Builder {
            mSnapshotHandler = snapshotHandler
            return this
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
        fun enablePhotoViewer(slideDuration:Duration, resolve:suspend (item:IMediaSource)->Bitmap?):Builder {
            mPhotoSlideShowDuration = slideDuration
            mPhotoResolver = resolve
            return this
        }

        @OptIn(UnstableApi::class)
        fun build():PlayerControllerModel {
            val playerModel = when {
                mSupportChapter && mPlaylist!=null -> PlaylistChapterPlayerModel(context, coroutineScope, mPlaylist!!, mAutoPlay, mContinuousPlay, mHideChapterViewIfEmpty)
                mSupportChapter -> ChapterPlayerModel(context, coroutineScope, mHideChapterViewIfEmpty)
                mPlaylist!=null -> PlaylistPlayerModel(context, coroutineScope, mPlaylist!!, mAutoPlay, mContinuousPlay)
                else -> BasicPlayerModel(context, coroutineScope)
            }
            if (mPhotoResolver!=null) {
                playerModel.enablePhotoViewer(mPhotoSlideShowDuration!!, mPhotoResolver!!)
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
    val rangePlayModel:StateFlow<RangedPlayModel?> = MutableStateFlow<RangedPlayModel?>(null)
    val hasNextRange = MutableStateFlow(false)
    val hasPrevRange = MutableStateFlow(false)
    val volume: MutableStateFlow<Float> get() = playerModel.volume  // 0-100
    val mute: MutableStateFlow<Boolean> get() = playerModel.mute
    val commandVolume = LiteUnitCommand()
    val commandChangeRange = LiteCommand<Boolean>(::changeRange)
    val isCurrentSourcePhoto: Flow<Boolean> = playerModel.currentSource.map { playerModel.isPhotoViewerEnabled && it?.isPhoto==true }

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
    val commandPause = LiteUnitCommand(playerModel::pause)
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

    val takingSnapshot: StateFlow<Boolean> = MutableStateFlow(false)
    private fun snapshot() {
        val handler = snapshotHandler ?: return
        val src = playerModel.currentSource.value ?: return

        if (src.isPhoto) {
            val bitmap = playerModel.resolvedBitmap ?: return
            handler(0, bitmap)
            return
        }

        takingSnapshot.mutable.value = true
        val pos = playerModel.currentPosition
        val rotation = Rotation.normalize(playerModel.rotation.value)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                TpFrameExtractor.create(playerModel.context, src.uri).use { extractor ->
                    val bitmap = extractor.extractFrame(pos)?.run {
                        if (rotation != 0) {
                            Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                        } else this
                    } ?: return@use
                    withContext(Dispatchers.Main) {
                        handler(pos, bitmap)
                    }
                }
            } finally {
                takingSnapshot.mutable.value = false
            }
        }
    }
    // endregion

    // region Slider

    /**
     * スライダーのトラッカー位置
     */
//    val sliderPosition = MutableStateFlow(0L)

    /**
     * プレーヤーの再生位置
     * 通常は、sliderPosition == presentingPosition だが、トリミングスライダーの場合は、左右トリミング用トラッカーも候補となる。
     * （最後に操作したトラッカーの位置が、presentingPosition となる。）
     */
//    open val presentingPosition:Flow<Long> = sliderPosition

//    fun seekAndSetSlider(pos:Long) {
//        val clipped = playerModel.clipPosition(pos)
////        sliderPosition.value = clipped
//        playerModel.seekTo(clipped)
//    }
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

//    init {
//        playerModel.playerSeekPosition.onEach(this::onPlayerSeekPositionChanged).launchIn(scope)
//    }

    /**
     * タイマーによって監視されるプレーヤーの再生位置（playerModel.playerSeekPosition）に応じて、スライダーのシーク位置を合わせる。
     */
//    open fun onPlayerSeekPositionChanged(pos:Long) {
//        sliderPosition.value = pos
//    }

    override fun close() {
        playerModel.close()
    }
}
