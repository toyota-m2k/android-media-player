package io.github.toyota32k.lib.player.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import io.github.toyota32k.binder.BaseBinding
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.utils.IDimension
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.LifecycleDisposer
import io.github.toyota32k.utils.StyledAttrRetriever
import io.github.toyota32k.utils.asMutableLiveData
import io.github.toyota32k.utils.disposableObserve
import io.github.toyota32k.utils.dp
import io.github.toyota32k.utils.dp2px
import io.github.toyota32k.utils.lifecycleOwner
import io.github.toyota32k.utils.px
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class PlayerSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = TpLib.logger
        const val DEF_RAIL_HEIGHT = 4
        const val DEF_ENABLED_RANGE_HEIGHT = 12
        const val DEF_MARKER_TICK_HEIGHT = DEF_ENABLED_RANGE_HEIGHT
        const val DEF_MARKER_TICK_WIDTH = 1
        const val DEF_MARKER_ICON_HEIGHT = 10
        const val DEF_MARKER_ICON_WIDTH = 5
        const val DEF_THUMB_HEIGHT = DEF_ENABLED_RANGE_HEIGHT + 4
        const val DEF_THUMB_WIDTH = 2
        const val DEF_UNDER_THUMB_WIDTH = 4
        const val DEF_RAIL_MARGIN_START = 5
        const val DEF_RAIL_MARGIN_END = 5
    }
    // region Slider Values
    private var onValueChanged: ((Long)->Unit)? = null
    private var onValueChangedByUser: ((Long)->Unit)? = null


    fun setValueChangedListener(listener:((Long)->Unit)?) {
        onValueChanged = listener
    }
    fun setValueChangedByUserListener(listener:((Long)->Unit)?) {
        onValueChangedByUser = listener
    }


    private var mPosition:Long = 0L
    private var mDuration:Long = 0L

    var position:Long
        get() = mPosition
        set(v) {
            setPositionNotNotify(v)
            onValueChanged?.invoke(v)
            if(dragging) {
                onValueChangedByUser?.invoke(v)
            }
        }
    fun setPositionNotNotify(value:Long) {
        val pos = clampPosition(value)
        if(pos != mPosition) {
            mPosition = pos
            invalidate()
        }
    }
    val naturalDuration:Long
        get() = mDuration

    /**
     * Duration（maxValue)をセットする
     * - 再生位置(position)はゼロにリセットされる。
     * - chapterListは、同時にセットすることもできるし、一旦クリアして、あとから setChapterList()でセットすることもできる。
     * - ただし、setDuration()より前に setChapterList()しても無効（このメソッドでクリアされる）
     * - 再生位置更新イベントが必要なら notify = true で呼ぶ。
     */
    fun setDuration(
        max:Long,
        chapterList:IChapterList?=null,
        notify:Boolean=false) {
        mPosition = 0L
        mDuration = max
        mPlayRange = null   // duration が変わったら play-range はクリア
        if(chapterList!=null) {
            this.chapterList = chapterList
            updateChapters(false)
        }
        invalidate()
        if(notify) {
            onValueChanged?.invoke(0L)
        }
    }
    // endregion

    // region Play Range

    val startPosition:Long get() = mPlayRange?.start ?: 0L
    val endPosition:Long get() = mPlayRange?.end ?: mDuration
    private val playLength:Long get() = endPosition - startPosition
    private var mPlayRange:Range? = null
//    val range:Range get() = mPlayRange ?: Range.empty

    private fun clampPosition(position: Long):Long {
        return position.coerceIn(startPosition, endPosition)
    }

    fun setPlayRange(range:Range?, redraw:Boolean=true) {
        if(range==mPlayRange) return    // 変更なし
        mPlayRange = if(range==null||!range.isTerminated) {
            null
        } else {
            range
        }
        mPlayRange = if(range==null) null else Range.terminate(range, mDuration)
        if(redraw) {
            invalidate()
        }
    }

    // endregion

    // region Support Chapter List

    private var chapterList:IChapterList? = null
    private val disposer = LifecycleDisposer()

    /**
     * Chapterリストを設定する
     */
    fun setChapterList(chapterList:IChapterList?) {
        this.chapterList = chapterList
        if(chapterList is IMutableChapterList) {
            disposer.reset()
            disposer.lifecycleOwner = lifecycleOwner()!!
            disposer + chapterList.modifiedListener.addForever { updateChapters() }
        }
        updateChapters()
    }
    /**
     * （チャプター編集中に）IChapterListの中味が変化した場合に呼び出す。
     */
    private fun updateChapters(redraw:Boolean=true) {
        markerPartsInfo.setMarkers(chapterList)
        enabledChapterInfo.setRanges(chapterList?.enabledRanges() ?: emptyList())
        disabledChapterInfo.setRanges(chapterList?.disabledRanges() ?: emptyList())
        if(redraw) {
            invalidate()
        }
    }

    // endregion

    // region 座標変換

    private fun positionToX(position:Long):Float {
        return (position-startPosition).toFloat() / playLength.toFloat() * sliderRange + leftMargin
    }
    private fun xToPosition(x:Float):Long {
        return ((x - leftMargin) / sliderRange * playLength).roundToLong() + startPosition
    }

    // endregion

    // region Draw Parts

    interface IPartsInfo {
        val description: String // for debug
        val verticalOffset:Int
        val height:Int
        val zOrder:Int

        val isValid:Boolean get() = height > 0
        val hasOffset:Boolean get() = verticalOffset != Int.MIN_VALUE

        fun draw(canvas: Canvas)
    }
    private val allParts get() = listOf(thumbPartsInfo, markerPartsInfo, railRightInfo, railLeftInfo, enabledChapterInfo, disabledChapterInfo, markerTickPartsInfo)
    private var drawingParts:List<IPartsInfo> = emptyList()
    private fun updateDrawableParts() {
        drawingParts = allParts.filter { it.isValid }.sortedBy { it.zOrder }
    }
    // endregion

    // region Icon Parts
    abstract inner class IconPartsInfo(val drawable:Drawable?, override val verticalOffset:Int, val width:Int, override val height:Int, val horizontalCenter: Int) : IPartsInfo {
        private val top:Int get() =  sliderTop + upperHeight + verticalOffset
        protected fun drawAt(canvas:Canvas, p:Long) {
            if(drawable!=null) {
                val left = (positionToX(p) - horizontalCenter).roundToInt()
                drawable.setBounds(left, top, left + width, top + height)
                drawable.draw(canvas)
            }
        }
    }

    /**
     * Thumb アイコン
     */
    inner class ThumbPartsInfo(drawable:Drawable?, verticalOffset:Int, width:Int, height:Int, horizontalCenter: Int, private val underThumbInfo:ThumbPartsInfo?=null): IconPartsInfo(drawable, verticalOffset, width, height, horizontalCenter) {
        override val description: String = "Thumb"
        override val zOrder: Int = Int.MAX_VALUE
        override fun draw(canvas: Canvas) {
            underThumbInfo?.draw(canvas)
            drawAt(canvas, position)
        }
    }
    private var thumbPartsInfo:ThumbPartsInfo = ThumbPartsInfo(null,0,0,0,0, null)
    private fun getDefaultThumbDrawable(context:Context):Drawable = AppCompatResources.getDrawable(context, R.drawable.ic_player_slider_thumb)!!

    private fun setUnderThumbAttrs(sar: StyledAttrRetriever, useCustomIcon:Boolean) :ThumbPartsInfo? {
        val drawable:Drawable
        val width: IDimension
        val height: IDimension
        if(!useCustomIcon) {
            drawable = getDefaultThumbDrawable(context)
            width = context.dp2px(DEF_UNDER_THUMB_WIDTH).px
            height = context.dp2px(DEF_THUMB_HEIGHT).px
        } else {
            drawable = sar.getDrawable(R.styleable.ControlPanel_ampUnderThumbIcon) ?:return null
            width = drawable.intrinsicWidth.px
            height = drawable.intrinsicHeight.px
        }
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampThumbVerticalOffset, -height/2)
        val w = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampUnderThumbIconWidth, width)
        val h = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampUnderThumbIconHeight, height)
        val horizontalCenter = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampUnderThumbHorizontalCenter, width/2)
        val tintColor = sar.getColor(R.styleable.ControlPanel_ampUnderThumbTintColor, com.google.android.material.R.attr.colorSurface, 0xFF000000.toInt())
        if(tintColor != 0) {
            drawable.setTint(tintColor)
        }
        return ThumbPartsInfo(drawable, verticalOffset, w, h, horizontalCenter)
    }

    private fun setThumbAttrs(sar: StyledAttrRetriever) :ThumbPartsInfo {
        val customIcon = sar.getDrawable(R.styleable.ControlPanel_ampThumbIcon)
        val drawable = customIcon ?: getDefaultThumbDrawable(context)
        val width: IDimension
        val height: IDimension
        if(customIcon==null) {
            // アイコンが指定されていなければ、デフォルト値を使用
            width = context.dp2px(DEF_THUMB_WIDTH).px
            height = context.dp2px(DEF_THUMB_HEIGHT).px
        } else {
            width = drawable.intrinsicWidth.px
            height = drawable.intrinsicHeight.px
        }
        val w = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampThumbIconWidth, width)
        val h = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampThumbIconHeight, height)
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampThumbVerticalOffset, -height/2)
        val horizontalCenter = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampThumbHorizontalCenter, width/2)
        val tintColor = sar.getColor(R.styleable.ControlPanel_ampThumbTintColor, com.google.android.material.R.attr.colorTertiaryFixed, com.google.android.material.R.attr.colorAccent, 0xFF00FFFF.toInt())

        if(tintColor != 0) {
            drawable.setTint(tintColor)
        }
        val underThumb = setUnderThumbAttrs(sar,  customIcon!=null)
        return ThumbPartsInfo(drawable, verticalOffset, w, h, horizontalCenter, underThumb).apply { thumbPartsInfo = this }
    }

    /**
     * Marker Icon
     */
    inner class MarkerPartsInfo(drawable: Drawable?, verticalOffset: Int, width: Int, height: Int, horizontalCenter: Int, override val zOrder: Int) : IconPartsInfo(drawable, verticalOffset, width, height, horizontalCenter) {
        override val description: String = "Marker"

        var markers:List<Long> = emptyList()
        override val isValid: Boolean
            get() = height>0 && showChapterBar

        fun setMarkers(chapterList: IChapterList?) {
            markers = chapterList?.chapters?.drop(1)?.map { it.position } ?: emptyList()
        }

        override fun draw(canvas: Canvas) {
            for(p in markers) {
                drawAt(canvas, p)
            }
        }
    }
    var markerPartsInfo: MarkerPartsInfo = MarkerPartsInfo(null, 0, 0, 0, 0, 0)
    private fun getDefaultMarkerDrawable(context:Context):Drawable = AppCompatResources.getDrawable(context, R.drawable.ic_player_slider_marker)!!
    private fun setMarkerAttrs(sar:StyledAttrRetriever) :MarkerPartsInfo {
        val customIcon = sar.getDrawable(R.styleable.ControlPanel_ampMarkerIcon)
        val drawable = customIcon ?: getDefaultMarkerDrawable(context)
        val width: IDimension
        val height: IDimension
        var zOrder:Int
        if(customIcon==null) {
            // アイコンが指定されていなければ、デフォルト値を使用
            width = context.dp2px(DEF_MARKER_ICON_WIDTH).px
            height = context.dp2px(DEF_MARKER_ICON_HEIGHT).px
            zOrder = 3
        } else {
            width = drawable.intrinsicWidth.px
            height = drawable.intrinsicHeight.px
            zOrder = Int.MAX_VALUE
        }
        val w = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampMarkerIconWidth, width)
        val h = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampMarkerIconHeight, height)
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampMarkerVerticalOffset, (context.dp2px(DEF_ENABLED_RANGE_HEIGHT)/2).px)
        val horizontalCenter = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampMarkerHorizontalCenter, width/2)
        val tintColor = sar.getColor(R.styleable.ControlPanel_ampMarkerTintColor, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF000000.toInt())
        zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampMarkerZOrder, zOrder)
        if(tintColor != 0) {
            drawable.setTint(tintColor)
        }
        return MarkerPartsInfo(drawable, verticalOffset, w, h, horizontalCenter, zOrder).apply { markerPartsInfo = this }
    }

    // endregion

    // region Range Parts (Rail)

    private fun paintOfColor(@ColorInt c:Int) : Paint {
        return Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            color = c
        }
    }

    abstract inner class RangePartsInfo(override val description: String, val paint: Paint, final override val height:Int, override val verticalOffset:Int, override val zOrder:Int) : IPartsInfo {
        constructor(description:String, @ColorInt color:Int, height:Int, verticalOffset:Int, zOrder:Int) : this(description, paintOfColor(color), height, verticalOffset, zOrder)

        init {
            paint.strokeWidth = height.toFloat()
        }
        private val top:Int get() =  sliderTop + upperHeight + verticalOffset
        val yCenter:Float get() = top + height/2f
        fun drawRange(canvas: Canvas, start:Long, end:Long) {
            if(end<=startPosition) return
            if(endPosition<=start) return
            if(end<=start) return

            val ex = positionToX(clampPosition(end))
            val sx = positionToX(clampPosition(start))
            val y = yCenter
            paint.alpha = if(isEnabled) 0xFF else 0x90
            paint.colorFilter =  if (isEnabled) null else ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            canvas.drawLine(sx,y,ex,y,paint)
        }
    }
    inner class RailRightInfo(
        @ColorInt color: Int,
        height:Int,
        verticalOffset:Int,
        zOrder:Int
    ) : RangePartsInfo("RailRight",color,height,verticalOffset,zOrder) {
        override fun draw(canvas: Canvas) {
            drawRange(canvas, position, endPosition)
        }
    }
    var railRightInfo:RailRightInfo = RailRightInfo(0,0,0,0)

    private fun setRailRightAttrs(sar: StyledAttrRetriever) :RailRightInfo {
        val color = sar.getColor(R.styleable.ControlPanel_ampRailRightColor, com.google.android.material.R.attr.colorOnPrimaryFixedVariant, com.google.android.material.R.attr.colorPrimaryVariant, Color.DKGRAY)
        val height = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRailRightHeight,DEF_RAIL_HEIGHT.dp)
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRailRightVerticalOffset,(-DEF_RAIL_HEIGHT / 2).dp)
        val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampRailRightZOrder, 1)
        return RailRightInfo(color,height,verticalOffset,zOrder).apply { railRightInfo = this }
    }

    inner class RailLeftInfo(
        @ColorInt color: Int,
        height:Int,
        verticalOffset:Int,
        zOrder:Int
    ) : RangePartsInfo("RailLeft", color,height,verticalOffset,zOrder) {
        override fun draw(canvas: Canvas) {
            drawRange(canvas, 0, position)
        }
    }
    var railLeftInfo:RailLeftInfo = RailLeftInfo(0,0,0,0)

    private fun setRailLeftAttrs(sar:StyledAttrRetriever) :RailLeftInfo {
        val color = sar.getColor(R.styleable.ControlPanel_ampRailLeftColor, com.google.android.material.R.attr.colorPrimaryFixed, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
        val height = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRailLeftHeight, DEF_RAIL_HEIGHT.dp)
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRailLeftVerticalOffset, (-DEF_RAIL_HEIGHT/2).dp)
        val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampRailLeftZOrder, 1)
        return RailLeftInfo(color,height,verticalOffset,zOrder).apply { railLeftInfo = this }
    }

    var showChapterBar = true
    inner class ChapterPartsInfo(
        description: String,
        @ColorInt color: Int,
        height:Int,
        verticalOffset:Int,
        zOrder:Int
    ) : RangePartsInfo(description, color,height,verticalOffset,zOrder) {
        private var ranges:List<Range> = emptyList()
        override val isValid: Boolean
            get() = height>0 && showChapterBar

        fun setRanges(ranges:List<Range>) {
            this.ranges = ranges
        }

        override fun draw(canvas: Canvas) {
            for(r in ranges) {
                drawRange(canvas, r.start, if(r.end==0L) naturalDuration else r.end)
            }
        }
    }

    var disabledChapterInfo:ChapterPartsInfo = ChapterPartsInfo("DisabledChapters",0,0,0,0)
    var enabledChapterInfo:ChapterPartsInfo = ChapterPartsInfo("EnabledChapters",0,0,0,0)

    private fun setEnabledChapterAttrs(sar: StyledAttrRetriever) :ChapterPartsInfo {
        val color = sar.getColor(R.styleable.ControlPanel_ampRangeEnabledColor,com.google.android.material.R.attr.colorSecondaryFixedDim, com.google.android.material.R.attr.colorSecondary, Color.GREEN)
        val height = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRangeEnabledHeight, DEF_ENABLED_RANGE_HEIGHT.dp)
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRangeEnabledVerticalOffset, (-DEF_ENABLED_RANGE_HEIGHT / 2).dp)
        val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampRangeEnabledZOrder, 0)
        return ChapterPartsInfo("EnabledChapters", color,height,verticalOffset,zOrder).apply { enabledChapterInfo = this }
    }

    private fun setDisabledChapterAttrs(sar: StyledAttrRetriever) :ChapterPartsInfo {
        val color = sar.getColor(R.styleable.ControlPanel_ampRangeDisabledColor,com.google.android.material.R.attr.colorOutline, 0xFF808080.toInt())
        val height = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRangeDisabledHeight,DEF_RAIL_HEIGHT.dp)
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRangeDisabledVerticalOffset,(-DEF_RAIL_HEIGHT / 2).dp)
        val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampRangeDisabledZOrder, 2)
        return ChapterPartsInfo("DisabledChapters",color,height,verticalOffset,zOrder).apply { disabledChapterInfo = this }
    }

    inner class MarkerTickPartsInfo(
        @ColorInt color: Int,
        val width: Int,
        height:Int,
        verticalOffset:Int,
        zOrder:Int) : RangePartsInfo("MarkerTick", color,height,verticalOffset,zOrder) {

        override val isValid: Boolean
            get() = height>0 && showChapterBar

        override fun draw(canvas: Canvas) {
            val d = width/2
            for(p in markerPartsInfo.markers) {
                val sx = positionToX(p) - d
                val ex = sx + width
                val y = yCenter
                canvas.drawLine(sx,y,ex,y,paint)
            }
        }
    }
    var markerTickPartsInfo:MarkerTickPartsInfo = MarkerTickPartsInfo(0,0,0,0,0)

    private fun setMarkerTickAttrs(sar: StyledAttrRetriever):MarkerTickPartsInfo {
        val color = sar.getColor(R.styleable.ControlPanel_ampRangeTickColor, com.google.android.material.R.attr.colorOutline, Color.BLACK)
        val width = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRangeTickWidth, DEF_MARKER_TICK_WIDTH.dp)
        val height = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRangeTickHeight, DEF_MARKER_TICK_HEIGHT.dp)
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRangeTickVerticalOffset, (-DEF_ENABLED_RANGE_HEIGHT / 2).dp)
        val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampRangeTickZOrder, 4)
        return MarkerTickPartsInfo(color,width,height,verticalOffset,zOrder).apply { markerTickPartsInfo = this }
    }

    // endregion
    fun setPlayerSliderAttributes(sar: StyledAttrRetriever, reLayout:Boolean=true) {
        if (!sar.sa.getBoolean(R.styleable.ControlPanel_ampAttrsByParent, false)) {
            setThumbAttrs(sar)
            setMarkerAttrs(sar)
            setRailLeftAttrs(sar)
            setRailRightAttrs(sar)
            setEnabledChapterAttrs(sar)
            setDisabledChapterAttrs(sar)
            setMarkerTickAttrs(sar)
            showChapterBar = sar.sa.getBoolean(R.styleable.ControlPanel_ampShowChapterBar, true)
            updateDrawableParts()
            staticMarginLeft = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRailMarginStart, DEF_RAIL_MARGIN_START.dp)
            staticMarginRight = sar.getDimensionPixelSize(R.styleable.ControlPanel_ampRailMarginEnd, DEF_RAIL_MARGIN_END.dp)
            if(reLayout) {
                calcLayoutBasis()
            }
        }
    }


    init {
        StyledAttrRetriever(context, attrs, R.styleable.ControlPanel, defStyleAttr, 0).use { sar ->
            try {
                setPlayerSliderAttributes(sar, false)
            } catch (e: Throwable) {
                logger.error(e)
                throw e
            }
        }
    }

    // static margin
    //
    private var staticMarginLeft:Int = 0
    private var staticMarginRight:Int = 0


    // 位置・サイズ
    private var upperHeight:Int = 0
    private var lowerHeight:Int = 0
    private var allOverHeight:Int = 0
    private var leftMargin:Float = 0f
    private var rightMargin:Float = 0f
    private var horizontalMargin:Float = 0f

    private fun calcLayoutBasis() {
        upperHeight = drawingParts.maxOfOrNull { -it.verticalOffset } ?: 0
        lowerHeight = drawingParts.maxOfOrNull { it.verticalOffset + it.height } ?: 0
        allOverHeight = lowerHeight+upperHeight
        leftMargin = maxOf(staticMarginLeft, thumbPartsInfo.horizontalCenter, markerPartsInfo.horizontalCenter).toFloat()
        rightMargin = maxOf(staticMarginRight, thumbPartsInfo.width-thumbPartsInfo.horizontalCenter, markerPartsInfo.width-markerPartsInfo.horizontalCenter).toFloat()
        horizontalMargin = leftMargin + rightMargin
        requestLayout()
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val width = when(widthMode) {
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST -> widthSize
            MeasureSpec.UNSPECIFIED -> 200
            else -> 200
        }

        // 当初、高さがNaturalHeightと異なる場合は、そのサイズになるよう拡大/縮小するために、mScale( = height / naturalHeight) を保持して位置調整していたが、
        // 初期化時にパーツのサイズ（特にextentWidth）が確定しないため、他の連動するビュー（フレームリストやプレーヤー）の位置調整ができなくなるので、
        // 高さは naturalHeight 固定とする。
        //
        // 変更前：3f4b7058dba6bd98a1f86d9e5c3d32b9820851c3
        // 変更後：da56b5b32b1ac2d5ec55fdf2d3920146f2e48c31


        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when(heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST-> min(allOverHeight, heightSize)
            MeasureSpec.UNSPECIFIED->allOverHeight
            else -> allOverHeight
        }
        setMeasuredDimension(width,height)
    }

    private var viewWidth = 0
    private var viewHeight = 0
    private var sliderRange = 0f
    private var sliderTop = 0

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = right - left
        val h = bottom - top
        if(viewWidth == w && viewHeight == h) {
            return
        }
        viewWidth = w
        viewHeight = h
        sliderRange = viewWidth - horizontalMargin
        sliderTop = ((viewHeight - allOverHeight)/2f).roundToInt()
    }

    override fun onDraw(canvas: Canvas) {
        if(naturalDuration==0L) return
        for(p in drawingParts) {
            p.draw(canvas)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
//        this.alpha = if(enabled) 1f else 0.5f
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handleTouchEvent(event.action, event.x, event.y)
    }

    private var dragging = false
    private fun handleTouchEvent(action:Int, x:Float, @Suppress("UNUSED_PARAMETER") y:Float):Boolean {
        if(!isEnabled) return false
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {}
            else -> { return false }
        }
        position = xToPosition(x).coerceIn(startPosition,endPosition)
        return true
    }

    class Binding(
        mode: BindingMode,
        override val data: LiveData<Long>,
        private val duration: LiveData<Long>? = null,
    ) : BaseBinding<Long>(mode) {
        private val slider:PlayerSlider? get() = view as? PlayerSlider
        private var durationObserved: IDisposable? = null

        fun connect(owner: LifecycleOwner, view: PlayerSlider) {
            super.connect(owner, view)
            if(duration!=null) {
                durationObserved = duration.disposableObserve(owner) { newDuration ->
                    slider?.setDuration(newDuration)
                }
            }
            if(mode!= BindingMode.OneWay) {
                view.setValueChangedListener(::onValueChangedBySlider)

                if (mode == BindingMode.OneWayToSource || data.value == null) {
                    slider?.apply {
                        onValueChangedBySlider(position)
                    }
                }
            }
        }

        private fun onValueChangedBySlider(v:Long) {
            if (data.value!=v) {
               mutableData?.value = v
            }
        }

        override fun dispose() {
            if(mode!= BindingMode.OneWay) {
                slider?.setValueChangedListener(null)
            }
            durationObserved?.dispose()
            durationObserved = null
            super.dispose()
        }

//        private fun clipByRange(a:Float, b:Float, v:Float):Float {
//            val min = java.lang.Float.min(a, b)
//            val max = java.lang.Float.max(a, b)
//            return java.lang.Float.min(java.lang.Float.max(min, v), max)
//        }
//
//        private fun fitToStep(v:Float, s:Float):Float {
//            return if(s==0f) {
//                v
//            } else {
//                @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
//                s*Math.round(v/s)
//            }
//        }

        override fun onDataChanged(v: Long?) {
            if(v!=null) {
                slider?.setPositionNotNotify(v)
            }
        }
    }
}

@Suppress("unused")
fun Binder.playerSliderBinding(slider: PlayerSlider, data: MutableStateFlow<Long>, duration: Flow<Long>? = null):Binder {
    add(PlayerSlider.Binding(BindingMode.TwoWay, data.asMutableLiveData(requireOwner), duration?.asLiveData()).apply { connect(requireOwner, slider) })
    return this
}
fun Binder.playerSliderBinding(slider: PlayerSlider, data: Flow<Long>, duration: Flow<Long>? = null): Binder {
    add(PlayerSlider.Binding(BindingMode.OneWay, data.asLiveData(), duration?.asLiveData()).apply { connect(requireOwner, slider) })
    return this
}