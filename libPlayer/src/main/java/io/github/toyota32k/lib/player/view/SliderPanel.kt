package io.github.toyota32k.lib.player.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.common.setMargin
import io.github.toyota32k.lib.player.databinding.V2SliderPanelBinding
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.view.PlayerSlider.Companion.DEF_RAIL_MARGIN_END
import io.github.toyota32k.lib.player.view.PlayerSlider.Companion.DEF_RAIL_MARGIN_START
import io.github.toyota32k.utils.GenericDisposable
import io.github.toyota32k.utils.android.StyledAttrRetriever
import io.github.toyota32k.utils.android.dp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SliderPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr)  {
    companion object {
        val logger get() = TpLib.logger
    }

    private val controls = V2SliderPanelBinding.inflate(LayoutInflater.from(context), this, true)
    private lateinit var model: PlayerControllerModel

    fun setSliderPanelAttributes(sar:StyledAttrRetriever) {
        if (!sar.sa.getBoolean(R.styleable.ControlPanel_ampAttrsByParent, false)) {
            val panelText = sar.getColor(
                R.styleable.ControlPanel_ampPanelForegroundColor,
                com.google.android.material.R.attr.colorOnSurface,
                def = Color.BLACK
            )
            controls.counterLabel.setTextColor(panelText)
            controls.durationLabel.setTextColor(panelText)

            val buttonTint = ControlPanel.createButtonColorStateList(sar)
            controls.nextRangeButton.imageTintList = buttonTint
            controls.prevRangeButton.imageTintList = buttonTint

            controls.counterLabel.setMargin( sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRailMarginStart, DEF_RAIL_MARGIN_START.dp),0,0,0)
            controls.durationLabel.setMargin(0,0,sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRailMarginEnd, DEF_RAIL_MARGIN_END.dp), 0)

            controls.playerSlider.setPlayerSliderAttributes(sar)
        }
    }

    init {
        StyledAttrRetriever(context, attrs, R.styleable.ControlPanel, defStyleAttr,0).use { sar ->
            setSliderPanelAttributes(sar)
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
            .textBinding(controls.durationLabel, combine(model.playerModel.naturalDuration, model.playerModel.playRange) {duration, range-> formatTime(range?.end?:duration, duration) } )
            .playerSliderBinding(controls.playerSlider, model.playerModel.playerSeekPosition, duration = model.playerModel.naturalDuration)
            .enableBinding(controls.playerSlider, model.playerModel.isReady)
            .multiVisibilityBinding(arrayOf(controls.prevRangeButton,controls.nextRangeButton), model.rangePlayModel.map { it!=null })
            .enableBinding(controls.prevRangeButton, model.hasPrevRange)
            .enableBinding(controls.nextRangeButton, model.hasNextRange)
            .enableBinding(controls.playerSlider, combine(model.playerModel.isReady,model.lockSlider) {ready, lock-> ready&&!lock })
//            .visibilityBinding(controls.sliderGuard, model.lockSlider)
            .bindCommand(model.commandChangeRange, controls.prevRangeButton, false)
            .bindCommand(model.commandChangeRange, controls.nextRangeButton, true)
            .add( GenericDisposable { controls.playerSlider.setValueChangedByUserListener(null) } )
            .observe(model.playerModel.currentSource) {src->
                val sourceWithChapter = src as? IMediaSourceWithChapter ?: return@observe
                controls.playerSlider.setChapterList(sourceWithChapter.chapterList)
            }
            .observe(model.playerModel.playRange) {
                controls.playerSlider.setPlayRange(it)
            }

    }

}