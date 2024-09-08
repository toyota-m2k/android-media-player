package io.github.toyota32k.lib.player.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.google.android.material.slider.Slider
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.sliderBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.lib.player.common.getColorAsDrawable
import io.github.toyota32k.lib.player.common.getColorAwareOfTheme
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.databinding.V2SliderPanelBinding
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.utils.GenericDisposable
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.disposableObserve
import io.github.toyota32k.utils.dp2px
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.roundToLong

class SliderPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr)  {
    companion object {
        val logger get() = TpLib.logger
    }

    val controls:V2SliderPanelBinding
    private lateinit var model: PlayerControllerModel

    init {
        val sa = context.theme.obtainStyledAttributes(attrs,
            R.styleable.ControlPanel, defStyleAttr,0)
        val panelBackground = sa.getColorAsDrawable(R.styleable.ControlPanel_panelBackgroundColor, context.theme, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val panelText = sa.getColorAwareOfTheme(R.styleable.ControlPanel_panelForegroundColor, context.theme, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val panelBottomPadding = sa.getDimensionPixelSize(R.styleable.ControlPanel_panelBottomPadding, context.dp2px(5))
        sa.recycle()

        controls = V2SliderPanelBinding.inflate(LayoutInflater.from(context), this, true).apply {
            sliderPanelRoot.background = panelBackground
            sliderPanelRoot.setPadding(0,0,0, panelBottomPadding)
            counterLabel.setTextColor(panelText)
            durationLabel.setTextColor(panelText)
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