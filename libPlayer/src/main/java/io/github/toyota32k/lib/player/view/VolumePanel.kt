package io.github.toyota32k.lib.player.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.sliderBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.databinding.V2VolumePanelBinding
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.utils.lifecycle.disposableObserve
import kotlinx.coroutines.flow.MutableStateFlow

class VolumePanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = TpLib.logger
    }
    @Suppress("MemberVisibilityCanBePrivate")
    val controls = V2VolumePanelBinding.inflate(LayoutInflater.from(context), this, true)
    private lateinit var model: PlayerControllerModel

    fun bindViewModel(model: PlayerControllerModel, binder: Binder) {
        this.model = model
        val sliderValue = MutableStateFlow(100f) // slider value は 0 -- 100 とする
        val muteCommand = LiteUnitCommand {
            model.mute.value = !model.mute.value
        }
        binder
            .visibilityBinding(controls.panelVolumeMutedButton, model.mute, boolConvert = BoolConvert.Straight, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.panelVolumeButton, model.mute, boolConvert = BoolConvert.Inverse, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .sliderBinding(controls.volumeSlider, sliderValue, mode=BindingMode.TwoWay)
            .add(sliderValue.disposableObserve(binder.requireOwner) { volume->
                model.volume.value = volume/100
            })
            .bindCommand(muteCommand, controls.panelVolumeButton, controls.panelVolumeMutedButton)
    }
}