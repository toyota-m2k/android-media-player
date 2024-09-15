package io.github.toyota32k.lib.player.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.databinding.V2SliderPanelBinding
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.utils.GenericDisposable
import io.github.toyota32k.utils.StyledAttrRetriever
import io.github.toyota32k.utils.disposableObserve
import kotlinx.coroutines.flow.map

class SliderPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr)  {
    companion object {
        val logger get() = TpLib.logger
    }

    private val controls:V2SliderPanelBinding
    private lateinit var model: PlayerControllerModel

    init {
        StyledAttrRetriever(context, attrs, R.styleable.ControlPanel, defStyleAttr,0).use { sar ->
//            val panelBackground = sar.getDrawableWithAlphaOnFallback(
//                R.styleable.ControlPanel_ampPanelBackgroundColor,
//                com.google.android.material.R.attr.colorSurface,
//                def = Color.WHITE, alpha = 0x50
//            )

            val panelText = sar.getColor(
                R.styleable.ControlPanel_ampPanelForegroundColor,
                com.google.android.material.R.attr.colorOnSurface,
                def = Color.BLACK
            )

            controls = V2SliderPanelBinding.inflate(LayoutInflater.from(context), this, true).apply {
                    counterLabel.setTextColor(panelText)
                    durationLabel.setTextColor(panelText)
                }
        }
    }

    @Suppress("unused")
    fun bindViewModel(model: PlayerControllerModel, binder: Binder) {
        this.model = model
        controls.playerSlider.setValueChangedByUserListener {
            model.playerModel.seekManager.requestedPositionFromSlider.value = it
        }

        binder
            .textBinding(controls.counterLabel, model.counterText)
            .textBinding(controls.durationLabel, model.playerModel.naturalDuration.map { formatTime(it,it) } )
            .playerSliderBinding(controls.playerSlider, model.playerModel.playerSeekPosition, duration = model.playerModel.naturalDuration)
            .enableBinding(controls.playerSlider, model.playerModel.isReady)
            .add( GenericDisposable { controls.playerSlider.setValueChangedByUserListener(null) } )
            .add( model.playerModel.currentSource.disposableObserve(binder.requireOwner) {src->
                val sourceWithChapter = src as? IMediaSourceWithChapter ?: return@disposableObserve
                controls.playerSlider.setChapterList(sourceWithChapter.chapterList)
            })
    }

}