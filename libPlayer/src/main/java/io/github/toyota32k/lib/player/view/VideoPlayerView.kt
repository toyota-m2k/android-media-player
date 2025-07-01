package io.github.toyota32k.lib.player.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.combinatorialVisibilityBinding
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.databinding.V2PlayerViewBinding
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.utils.android.StyledAttrRetriever
import io.github.toyota32k.utils.gesture.IUtManipulationTarget
import io.github.toyota32k.utils.gesture.UtAbstractManipulationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Suppress("unused")
class VideoPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = TpLib.logger
    }

    @Suppress("MemberVisibilityCanBePrivate")
    val controls:V2PlayerViewBinding =
        V2PlayerViewBinding.inflate(LayoutInflater.from(context), this, true)

    fun setVideoPlayerViewAttributes(sar: StyledAttrRetriever) {
        if (!sar.sa.getBoolean(R.styleable.ControlPanel_ampAttrsByParent, false)) {
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
            .combinatorialVisibilityBinding(model.playerModel.currentSource.map { model.playerModel.isPhotoViewerEnabled && it?.isPhoto == true }) {
                straightInvisible(controls.photoView)
                inverseInvisible(controls.player)
            }
            .clickBinding(controls.volumeGuardView) {
                showVolumePanel.value = false
            }
            .bindCommand(model.commandVolume) {
                if (model.playerModel.currentSource.value?.isPhoto != true) {
                    showVolumePanel.value = true
                }
            }
            .conditional( model.playerModel.isPhotoViewerEnabled ) {
                observe(model.playerModel.currentSource) {
                    if(it?.isPhoto == true) {
                        CoroutineScope(Dispatchers.Main).launch {
                            val bitmap = model.playerModel.resolvePhoto(it)
                            controls.photoView.setImageBitmap(bitmap)
                        }
                    } else {
                        controls.photoView.setImageBitmap(null)
                        model.playerModel.resetPhoto()
                    }
                }
            }
    }

    fun associatePlayer() {
        controls.player.associatePlayer()
    }
    fun dissociatePlayer() {
        controls.player.dissociatePlayer()
    }

    /**
     * VideoPlayerView にズーム機能を付加するための最小限のIUtManipulationTarget実装
     */
    class SimpleManipulationTarget(override val parentView: View, override val contentView: View) : UtAbstractManipulationTarget()
    inner class ExtendedManipulationTarget : UtAbstractManipulationTarget() {
        override val parentView: View
            get() = controls.root
        override val contentView: View
            get() = if(model.playerModel.currentSource.value?.isPhoto==true) {
                    controls.photoView
                } else {
                    controls.player
                }
    }

    val manipulationTarget: IUtManipulationTarget
        get() = if(model.playerModel.isPhotoViewerEnabled) ExtendedManipulationTarget() else SimpleManipulationTarget(controls.root, controls.photoView)
}