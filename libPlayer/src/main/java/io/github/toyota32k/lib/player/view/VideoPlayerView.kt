package io.github.toyota32k.lib.player.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.databinding.V2PlayerViewBinding
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.VisibleAreaParams
import io.github.toyota32k.utils.android.StyledAttrRetriever
import io.github.toyota32k.utils.gesture.IUtManipulationTarget
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("unused")
class VideoPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = TpLib.logger
    }

    @Suppress("MemberVisibilityCanBePrivate")
    val controls:V2PlayerViewBinding =
        V2PlayerViewBinding.inflate(LayoutInflater.from(context), this, true)

    private fun setVideoPlayerViewAttributes(sar: StyledAttrRetriever) {
        if (sar.sa.getBoolean(R.styleable.ControlPanel_ampAttrsByParent, true)) {
            controls.player.setPlayerAttributes(sar)
            controls.controller.setControlPanelAttributes(sar)
        }
    }


    init {
        StyledAttrRetriever(context, attrs, R.styleable.ControlPanel, defStyleAttr,0).use { sar ->
            setVideoPlayerViewAttributes(sar)
        }

    }

    private lateinit var model: PlayerControllerModel

    fun bindViewModel(model: PlayerControllerModel, binder: Binder) {
        this.model = model
        controls.player.bindViewModel(model, binder)
        controls.controller.bindViewModel(model, binder)
        controls.volumePanel.bindViewModel(model, binder)
        val showVolumePanel = MutableStateFlow(false)
        binder
            .visibilityBinding(controls.controller, model.showControlPanel, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .multiVisibilityBinding(arrayOf(controls.volumePanel,controls.volumeGuardView), showVolumePanel, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .clickBinding(controls.volumeGuardView) {
                showVolumePanel.value = false
            }
            .bindCommand(model.commandVolume) {
                if (model.playerModel.currentSource.value?.isPhoto != true) {
                    showVolumePanel.value = true
                }
            }
    }

    fun associatePlayer() {
        controls.player.associatePlayer()
    }
    fun dissociatePlayer() {
        controls.player.dissociatePlayer()
    }

//    inner class ExtendedManipulationTarget : UtAbstractManipulationTarget() {
//        override val parentView: View
//            get() = controls.root
//        override val contentView: View
//            get() = if(model.playerModel.currentSource.value?.isPhoto==true) {
//                    controls.photoView
//                } else {
//                    controls.player
//                }
//    }

    val manipulationTarget: IUtManipulationTarget
        get() = controls.player.manipulationTarget // SimpleManipulationTarget(controls.root, controls.player) // if(model.playerModel.isPhotoViewerEnabled) ExtendedManipulationTarget() else SimpleManipulationTarget(controls.root, controls.photoView)
    val visibleAreaParams: VisibleAreaParams?
        get() = controls.player.getVisibleAreaParams()
}