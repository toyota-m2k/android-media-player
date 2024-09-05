package io.github.toyota32k.lib.player.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.utils.Listeners
import io.github.toyota32k.utils.UtLog
import kotlin.math.max

class PlayerSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = TpLib.logger
    }

    data class DrawablePartsInfo(
        val drawable:Drawable,
        val verticalOffset:Int,
        val width:Int,
        val height:Int,
        val horizontalCenter: Int
        ) {
        val top:Int get() = verticalOffset
        val bottom:Int get() = verticalOffset+height

        companion object {
            fun create(
                drawable:Drawable?,
                verticalOffset:Int,
                width:Int,
                height:Int,
                horizontalCenter: Int,
                autoNegativeOffset:Boolean
                ):DrawablePartsInfo {
                if (drawable==null) throw IllegalArgumentException("drawable is null")
                val w = if (width<0) drawable.intrinsicWidth else width
                val h = if (height<0) drawable.intrinsicHeight else height
                val offset = if(verticalOffset==Int.MIN_VALUE) {
                    if (autoNegativeOffset) -h else 0
                } else {
                    verticalOffset
                }
                return DrawablePartsInfo(
                    drawable,
                    offset,
                    w, h,
                    if (horizontalCenter<0) (w+1)/2 else horizontalCenter)
            }
        }
    }

    data class PaintPartsInfo(
        @ColorInt val color:Int,
        val height:Int,
        val verticalOffset:Int,
        val zOrder:Int) {
        val top:Int get() = verticalOffset
        val bottom:Int get() = verticalOffset+height
        companion object {
            fun create(
                @ColorInt color:Int,
                height:Int,
                verticalOffset:Int,
                zOrder:Int) : PaintPartsInfo {
                return PaintPartsInfo(color, height, verticalOffset, zOrder)
            }
        }
    }

    // Slider Value
    private var onValueChanged: ((Float)->Unit)? = null
    fun setValueChangedListener(listener:((Float)->Unit)?) {
        onValueChanged = listener
    }


    private var mValue:Float = 0f
    var value:Float
        get() = mValue
        set(v) {
            mValue = v
            invalidate()
        }
    fun setValueAndNotify(value:Float) {
        mValue = value
        invalidate()
        onValueChanged?.invoke(value)
    }
    var minValue:Float = 0f
        set(v) {
            field = v
            invalidate()
        }
    var maxValue:Float = 1f
        set(v) {
            field = v
            invalidate()
        }


    // アイコンパーツ
    private val thumb : DrawablePartsInfo          // Thumbのアイコン
    private val marker : DrawablePartsInfo         // Chapter Marker のアイコン

    // べた塗パーツ
    private val railGuide: PaintPartsInfo          // レール：ガイドライン
    private val railRight: PaintPartsInfo         // レール：未再生ゾーン（Thumbの右）
    private val railLeft: PaintPartsInfo          // レール：再生済みゾーン (Thumbの左）
    private val rangeEnabled : PaintPartsInfo     // レール：再生禁止ゾーン
    private val rangeDisabled : PaintPartsInfo     // レール：再生禁止ゾーン
    private val railTick: PaintPartsInfo        // レール上の区切り線

    init {
        val sa = context.theme.obtainStyledAttributes(attrs, R.styleable.PlayerSlider, defStyleAttr, 0)
        try {
            thumb = DrawablePartsInfo.create(
                sa.getDrawable(R.styleable.PlayerSlider_thumb),
                sa.getDimensionPixelSize(R.styleable.PlayerSlider_thumbVerticalOffset, Int.MIN_VALUE),
                sa.getDimensionPixelSize(R.styleable.PlayerSlider_thumbWidth, -1),
                sa.getDimensionPixelSize(R.styleable.PlayerSlider_thumbHeight, -1),
                sa.getDimensionPixelSize(R.styleable.PlayerSlider_thumbHorizontalCenter, -1), false)
            marker = DrawablePartsInfo.create(
                sa.getDrawable(R.styleable.PlayerSlider_marker),
                sa.getDimensionPixelSize(R.styleable.PlayerSlider_markerVerticalOffset, Int.MIN_VALUE),
                sa.getDimensionPixelSize(R.styleable.PlayerSlider_markerWidth, -1),
                sa.getDimensionPixelSize(R.styleable.PlayerSlider_markerHeight, -1),
                sa.getDimensionPixelSize(R.styleable.PlayerSlider_markerHorizontalCenter, -1), true)
            railGuide = PaintPartsInfo.create(
                sa.getColor(R.styleable.PlayerSlider_railGuideColor,0),
                sa.getDimensionPixelSize(R.styleable.PlayerSlider_railGuideHeight, 1),
                sa.getDimensionPixelSize(R.styleable.PlayerSlider_railGuideVerticalOffset, Int.MIN_VALUE),
                sa.getInt(R.styleable.PlayerSlider_railGuideZOrder, 0))


        } finally {
            sa.recycle()
        }
    }

    // 位置・サイズ
    val upperHeight:Int by lazy { -minOf(thumb.top, marker.top, railBase.top, railRight.top, railLeft.top, railDisabled.top, railTick.top) }
    val lowerHeight:Int by lazy { maxOf(thumb.bottom, marker.bottom, railBase.bottom, railRight.bottom, railLeft.bottom, railDisabled.bottom, railTick.bottom)}
    val allOverHeight:Int by lazy { upperHeight+lowerHeight }
    val horizontalMargin:Int by lazy { (max(thumb.width, marker.width)+1)/2 }
}