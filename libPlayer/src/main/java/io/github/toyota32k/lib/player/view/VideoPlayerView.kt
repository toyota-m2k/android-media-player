package io.github.toyota32k.lib.player.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.databinding.V2PlayerViewBinding
import io.github.toyota32k.utils.UtLog

@Suppress("unused")
class VideoPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger by lazy { UtLog("PlayerView", TpLib.logger) }
    }

    val controls:V2PlayerViewBinding

    init {
        controls = V2PlayerViewBinding.inflate(LayoutInflater.from(context), this, true)
    }

    private lateinit var model: PlayerControllerModel

    fun bindViewModel(model: PlayerControllerModel, binder: Binder) {
        this.model = model
        controls.player.bindViewModel(model, binder)
        controls.controller.bindViewModel(model, binder)
        binder
            .visibilityBinding(controls.controller, model.showControlPanel, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
    }

    fun associatePlayer() {
        controls.player.associatePlayer()
    }
    fun dissociatePlayer() {
        controls.player.dissociatePlayer()
    }
}