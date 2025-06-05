package io.github.toyota32k.lib.player.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.view.children
import com.google.android.material.slider.Slider
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.databinding.V2ControlPanelBinding
import io.github.toyota32k.lib.player.model.IChapterHandler
import io.github.toyota32k.lib.player.model.IPlaylistHandler
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.Rotation
import io.github.toyota32k.utils.android.StyledAttrRetriever
import io.github.toyota32k.utils.gesture.UtClickRepeater
import io.github.toyota32k.utils.lifecycle.ConstantLiveData
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.math.roundToLong

class ControlPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), Slider.OnChangeListener {
    companion object {
        val logger get() = TpLib.logger
        fun createButtonColorStateList(sar:StyledAttrRetriever):ColorStateList {
            val buttonEnabled = sar.getColor(
                R.styleable.ControlPanel_ampButtonTintColor,
                com.google.android.material.R.attr.colorPrimary,
                Color.BLACK
            )
            val buttonDisabled = sar.getColorWithAlphaOnFallback(
                R.styleable.ControlPanel_ampButtonDisabledTintColor,
                com.google.android.material.R.attr.colorOnSurface,
                Color.BLACK, alpha = 0x50
            )
            return ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(),
                ),
                intArrayOf(buttonEnabled, buttonDisabled)
            )
        }
    }

    val controls = V2ControlPanelBinding.inflate(LayoutInflater.from(context), this, true)

    fun setControlPanelAttributes(sar:StyledAttrRetriever) {
        if (!sar.sa.getBoolean(R.styleable.ControlPanel_ampAttrsByParent, false)) {
            val panelBackground = sar.getDrawableWithAlphaOnFallback(
                R.styleable.ControlPanel_ampPanelBackgroundColor,
                com.google.android.material.R.attr.colorSurface,
                def = Color.WHITE, alpha = 0x50
            )

            val buttonTint = createButtonColorStateList(sar)
            val padding = sar.sa.getDimensionPixelSize(R.styleable.ControlPanel_ampPanelPadding, 0)
            val paddingStart = sar.sa.getDimensionPixelSize(R.styleable.ControlPanel_ampPanelPaddingStart, padding)
            val paddingTop = sar.sa.getDimensionPixelSize(R.styleable.ControlPanel_ampPanelPaddingTop, padding)
            val paddingEnd = sar.sa.getDimensionPixelSize(R.styleable.ControlPanel_ampPanelPaddingEnd, padding)
            val paddingBottom = sar.sa.getDimensionPixelSize(R.styleable.ControlPanel_ampPanelPaddingBottom, padding)

            controls.apply {
                controlPanelRoot.background = panelBackground
                controlPanelRoot.setPadding(paddingStart, paddingTop, paddingEnd, paddingBottom)
                controlButtons.children.forEach { (it as? ImageButton)?.imageTintList = buttonTint }
            }

            controls.sliderPanel.setSliderPanelAttributes(sar)
        }
    }


    init {
        StyledAttrRetriever(context, attrs,R.styleable.ControlPanel, defStyleAttr,0).use { sar ->
            setControlPanelAttributes(sar)
        }
    }

    private lateinit var model: PlayerControllerModel

    fun bindViewModel(model: PlayerControllerModel, binder: Binder) {
        this.model = model

//        controls.slider.addOnChangeListener(this)
//        controls.chapterView.bindViewModel(model.playerModel, binder)
        controls.sliderPanel.bindViewModel(model, binder)

        val chapterHandler = model.playerModel as? IChapterHandler
        val playlistHandler = model.playerModel as? IPlaylistHandler

        binder
            .visibilityBinding(controls.playButton, model.playerModel.isPlaying, BoolConvert.Inverse, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.pauseButton, model.playerModel.isPlaying, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.fullscreenButton, model.windowMode.map { model.supportFullscreen && it!=PlayerControllerModel.WindowMode.FULLSCREEN }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.collapseButton, model.windowMode.map { model.supportFullscreen && it!=PlayerControllerModel.WindowMode.NORMAL }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.pinpButton, ConstantLiveData(model.supportPinP), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.snapshotButton, ConstantLiveData(model.snapshotHandler!=null), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.rotateLeft, ConstantLiveData(model.enableRotateLeft), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.rotateRight, ConstantLiveData(model.enableRotateRight), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.volumeButton, model.mute.map { !it && model.enableVolumeController }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.volumeMutedButton, model.mute.map { it && model.enableVolumeController }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .multiVisibilityBinding(arrayOf(controls.seekBackLButton,controls.seekForwardLButton), ConstantLiveData(model.seekLarge!=null), BoolConvert.Straight,VisibilityBinding.HiddenMode.HideByGone)
            .multiVisibilityBinding(arrayOf(controls.seekBackMButton,controls.seekForwardMButton), ConstantLiveData(model.seekMedium!=null), BoolConvert.Straight,VisibilityBinding.HiddenMode.HideByGone)
            .multiVisibilityBinding(arrayOf(controls.seekBackSButton,controls.seekForwardSButton), ConstantLiveData(model.seekSmall!=null), BoolConvert.Straight,VisibilityBinding.HiddenMode.HideByGone)
            .multiVisibilityBinding(arrayOf(controls.prevChapterButton, controls.nextChapterButton), ConstantLiveData(chapterHandler!=null), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .multiVisibilityBinding(arrayOf(controls.prevVideoButton, controls.nextVideoButton), ConstantLiveData(playlistHandler!=null && model.showNextPreviousButton), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .multiEnableBinding(arrayOf(
                controls.playButton,
                controls.pauseButton,
                controls.seekBackLButton,
                controls.seekBackMButton,
                controls.seekBackSButton,
                controls.seekForwardLButton,
                controls.seekForwardMButton,
                controls.seekForwardSButton,
                controls.prevChapterButton,
                controls.nextChapterButton,
                controls.rotateLeft,
                controls.rotateRight,
                controls.fullscreenButton,
                controls.pinpButton), model.playerModel.isReady)
            .enableBinding(controls.snapshotButton, combine(model.playerModel.isReady,model.takingSnapshot) { r, s -> r && !s })
            .bindCommand(model.commandPlay, controls.playButton)
            .bindCommand(model.commandPlay, controls.playButton)
            .bindCommand(model.commandPause, controls.pauseButton)
            .bindCommand(model.commandLockSlider, controls.lockSliderButton, controls.unlockSliderButton)
            .bindCommand(model.commandVolume, controls.volumeButton, controls.volumeMutedButton)
            .visibilityBinding(controls.lockSliderButton, model.lockSlider.map { model.enableSliderLock && !it }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.unlockSliderButton, model.lockSlider.map { model.enableSliderLock && it }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .conditional(model.seekLarge!=null) {
                bindCommand(model.commandSeekLarge, controls.seekBackLButton, false)
                bindCommand(model.commandSeekLarge, controls.seekForwardLButton, true)
                add(UtClickRepeater(controls.seekBackLButton))
                add(UtClickRepeater(controls.seekForwardLButton))
            }
            .conditional(model.seekMedium!=null) {
                bindCommand(model.commandSeekMedium, controls.seekBackMButton, false)
                bindCommand(model.commandSeekMedium, controls.seekForwardMButton, true)
                add(UtClickRepeater(controls.seekBackMButton))
                add(UtClickRepeater(controls.seekForwardMButton))
            }
            .conditional(model.seekSmall!=null) {
                bindCommand(model.commandSeekSmall, controls.seekBackSButton, false)
                bindCommand(model.commandSeekSmall, controls.seekForwardSButton, true)
                add(UtClickRepeater(controls.seekBackSButton))
                add(UtClickRepeater(controls.seekForwardSButton))
            }
            .bindCommand(model.commandFullscreen, controls.fullscreenButton)
            .bindCommand(model.commandSnapshot, controls.snapshotButton)
            .bindCommand(model.commandPinP, controls.pinpButton)
            .bindCommand(model.commandCollapse, controls.collapseButton)
            .bindCommand(model.commandRotate, controls.rotateLeft, Rotation.LEFT)
            .bindCommand(model.commandRotate, controls.rotateRight, Rotation.RIGHT)
            .apply {
                if(playlistHandler!=null ) {
                    enableBinding(controls.prevVideoButton, playlistHandler.hasPrevious)
                    enableBinding(controls.nextVideoButton, playlistHandler.hasNext)
                    bindCommand(playlistHandler.commandNext, controls.nextVideoButton)
                    bindCommand(playlistHandler.commandPrev, controls.prevVideoButton)
                }
                if(chapterHandler!=null) {
                    bindCommand(chapterHandler.commandNextChapter, controls.nextChapterButton)
                    bindCommand(chapterHandler.commandPrevChapter, controls.prevChapterButton)
                }
            }
    }

    @SuppressLint("RestrictedApi")
    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if(fromUser) {
//            logger.debug("XX2: ${value.roundToLong()} / ${slider.valueTo.roundToLong()}")
            model.playerModel.seekManager.requestedPositionFromSlider.value = value.roundToLong()
        }
    }
}