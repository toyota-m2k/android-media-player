package io.github.toyota32k.lib.player.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
import io.github.toyota32k.lib.player.common.IDimension
import io.github.toyota32k.lib.player.common.StyledAttrRetriever
import io.github.toyota32k.lib.player.common.dp
import io.github.toyota32k.lib.player.common.px
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.LifecycleDisposer
import io.github.toyota32k.utils.asMutableLiveData
import io.github.toyota32k.utils.disposableObserve
import io.github.toyota32k.utils.lifecycleOwner
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
        const val DEF_MARKER_TICK_WIDTH = 2
        const val DEF_MARKER_ICON_HEIGHT = 10
        const val DEF_THUMB_HEIGHT = DEF_ENABLED_RANGE_HEIGHT + 6
        const val DEF_THUMB_WIDTH = 2
        const val DEF_UNDER_THUMB_WIDTH = 4
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
    private var mDuration:Long = 100L

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
        val pos = max(0, min(duration, value))
        if(pos != mPosition) {
            mPosition = pos
            invalidate()
        }
    }
    val duration:Long
        get() = mDuration

    /**
     * Duration（maxValue)をセットする
     * - 再生位置(position)はゼロにリセットされる。
     * - chapterListは、同時にセットすることもできるし、一旦クリアして、あとから setChapterList()でセットすることもできる。
     * - ただし、setDuration()より前に setChapterList()しても無効（このメソッドでクリアされる）
     * - 再生位置更新イベントが必要なら notify = true で呼ぶ。
     */
    fun setDuration(max:Long, chapterList:IChapterList?=null, notify:Boolean=false) {
        mPosition = 0L
        mDuration = max
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
        enableedChapterInfo.setRanges(chapterList?.enabledRanges() ?: emptyList())
        disabledChapterInfo.setRanges(chapterList?.disabledRanges() ?: emptyList())
        if(redraw) {
            invalidate()
        }
    }

    // endregion

    // region 座標変換

    private fun positionToX(position:Long):Float {
        return (position.toFloat() / duration.toFloat()) * sliderRange + leftMargin
    }
    private fun xToPosition(x:Float):Long {
        return ((x - leftMargin) / sliderRange * duration.toFloat()).roundToLong()
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
    private val allParts get() = listOf(thumbPartsInfo, markerPartsInfo, railRightInfo, railLeftInfo, enableedChapterInfo, disabledChapterInfo, markerTickPartsInfo)
    private var drawingParts:List<IPartsInfo> = emptyList()
    private fun updateDrawableParts() {
        drawingParts = allParts.filter { it.isValid }.sortedBy { it.zOrder }
    }
    // endregion

    // region Icon Parts
    abstract inner class IconPartsInfo(val drawable:Drawable, override val verticalOffset:Int, val width:Int, override val height:Int, val horizontalCenter: Float) : IPartsInfo {
        private val top:Int get() =  sliderTop + upperHeight + verticalOffset
        protected fun drawAt(canvas:Canvas, p:Long) {
            val left = (positionToX(p) - horizontalCenter).roundToInt()
            drawable.setBounds(left, top, left + width, top + height)
            drawable.draw(canvas)
        }
//        private fun hitTest(x:Int): Boolean {
//            val left = (x - horizontalCenter).roundToInt()
//            return left<=x && x<=left+width
//        }
//        fun hitTest(x:Int, y:Int) :Boolean {
//            val top = upperHeight - verticalOffset
//            return hitTest(x) && top <= y && y <= top + height
//        }
    }

    /**
     * Thumb アイコン
     */
    inner class ThumbPartsInfo(drawable:Drawable, verticalOffset:Int, width:Int, height:Int, horizontalCenter: Float, val underThumbInfo:ThumbPartsInfo?=null): IconPartsInfo(drawable, verticalOffset, width, height, horizontalCenter) {
        override val description: String = "Thumb"
        override val zOrder: Int = Int.MAX_VALUE
        override fun draw(canvas: Canvas) {
            underThumbInfo?.draw(canvas)
            drawAt(canvas, position)
        }
    }
    private lateinit var thumbPartsInfo:ThumbPartsInfo
    private fun getDefaultThumbDrawable(context:Context):Drawable = AppCompatResources.getDrawable(context, R.drawable.ic_player_slider_thumb)!!

    private fun setUnderThumbAttrs(sar: StyledAttrRetriever, useCustomIcon:Boolean) :ThumbPartsInfo? {
        val drawable:Drawable
        val width: IDimension
        val height: IDimension
        if(!useCustomIcon) {
            drawable = getDefaultThumbDrawable(context)
            width = DEF_UNDER_THUMB_WIDTH.dp
            height = DEF_THUMB_HEIGHT.dp
        } else {
            drawable = sar.getDrawable(R.styleable.PlayerSlider_ampUnderThumbIcon) ?:return null
            width = drawable.intrinsicWidth.px
            height = drawable.intrinsicHeight.px
        }
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampThumbVerticalOffset, -height/2)
        val w = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampUnderThumbIconWidth, width)
        val h = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampUnderThumbIconHeight, height)
        val horizontalCenter = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampUnderThumbHorizontalCenter, width/2).toFloat()
        val tintColor = sar.getColor(R.styleable.PlayerSlider_ampUnderThumbTintColor, com.google.android.material.R.attr.colorSurface, 0xFF000000.toInt())
        if(tintColor != 0) {
            drawable.setTint(tintColor)
        }
        return ThumbPartsInfo(drawable, verticalOffset, w, h, horizontalCenter)
    }

    private fun setThumbAttrs(sar: StyledAttrRetriever) :ThumbPartsInfo {
        val customIcon = sar.getDrawable(R.styleable.PlayerSlider_ampThumbIcon)
        val drawable = customIcon ?: getDefaultThumbDrawable(context)
        val width: IDimension
        val height: IDimension
        if(customIcon==null) {
            // アイコンが指定されていなければ、デフォルト値を使用
            width = DEF_THUMB_WIDTH.dp
            height = DEF_THUMB_HEIGHT.dp
        } else {
            width = drawable.intrinsicWidth.px
            height = drawable.intrinsicHeight.px
        }
        val w = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampThumbIconWidth, width)
        val h = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampThumbIconHeight, height)
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampThumbVerticalOffset, -height/2)
        val horizontalCenter = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampThumbHorizontalCenter, width/2).toFloat()
        val tintColor = sar.getColor(R.styleable.PlayerSlider_ampThumbTintColor, com.google.android.material.R.attr.colorTertiaryFixed, com.google.android.material.R.attr.colorAccent, 0xFF00FFFF.toInt())

        if(tintColor != 0) {
            drawable.setTint(tintColor)
        }
        val underThumb = setUnderThumbAttrs(sar,  customIcon!=null)
        return ThumbPartsInfo(drawable, verticalOffset, w, h, horizontalCenter, underThumb).apply { thumbPartsInfo = this }
    }

    /**
     * Marker Icon
     */
    inner class MarkerPartsInfo(drawable: Drawable, verticalOffset: Int, width: Int, height: Int, horizontalCenter: Float, override val zOrder: Int) : IconPartsInfo(drawable, verticalOffset, width, height, horizontalCenter) {
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
    lateinit var markerPartsInfo: MarkerPartsInfo
    private fun getDefaultMarkerDrawable(context:Context):Drawable = AppCompatResources.getDrawable(context, R.drawable.ic_player_slider_marker)!!
    private fun setMarkerAttrs(sar:StyledAttrRetriever) :MarkerPartsInfo {
        val customIcon = sar.getDrawable(R.styleable.PlayerSlider_ampMarkerIcon)
        val drawable = customIcon ?: getDefaultMarkerDrawable(context)
        val width: IDimension
        val height: IDimension
        var zOrder:Int
        if(customIcon==null) {
            // アイコンが指定されていなければ、デフォルト値を使用
            width = DEF_MARKER_ICON_HEIGHT.dp
            height = DEF_MARKER_ICON_HEIGHT.dp
            zOrder = 3
        } else {
            width = drawable.intrinsicWidth.px
            height = drawable.intrinsicHeight.px
            zOrder = Int.MAX_VALUE
        }
        val w = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampMarkerIconWidth, width)
        val h = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampMarkerIconHeight, height)
        val verticalOffset = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampMarkerVerticalOffset, (DEF_RAIL_HEIGHT / 2).dp)
        val horizontalCenter = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampMarkerHorizontalCenter, width/2).toFloat()
        val tintColor = sar.getColor(R.styleable.PlayerSlider_ampMarkerTintColor, com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt())
        zOrder = sar.sa.getInt(R.styleable.PlayerSlider_ampMarkerZOrder, zOrder)
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
            val sx = positionToX(start)
            val ex = positionToX(end)
            val y = yCenter
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
            drawRange(canvas, position, duration)
        }
    }
    private lateinit var railRightInfo:RailRightInfo
    private fun setRailRightAttrs(@ColorInt color: Int, height:Int, verticalOffset:Int, zOrder:Int) :RailRightInfo {
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
    private lateinit var railLeftInfo:RailLeftInfo
    private fun setRailLeftAttrs(@ColorInt color: Int, height:Int, verticalOffset:Int, zOrder:Int) :RailLeftInfo {
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
                drawRange(canvas, r.start, if(r.end==0L) duration else r.end)
            }
        }
    }
    private lateinit var disabledChapterInfo:ChapterPartsInfo
    private lateinit var enableedChapterInfo:ChapterPartsInfo
    private fun setEnabledChapterAttrs(@ColorInt color: Int, height:Int, verticalOffset:Int, zOrder:Int) :ChapterPartsInfo {
        return ChapterPartsInfo("EnabledChapters", color,height,verticalOffset,zOrder).apply { enableedChapterInfo = this }
    }
    private fun setDisabledChapterAttrs(@ColorInt color: Int, height:Int, verticalOffset:Int, zOrder:Int) :ChapterPartsInfo {
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
    private lateinit var markerTickPartsInfo:MarkerTickPartsInfo
    private fun setMarkerTickAttrs(@ColorInt color: Int, width:Int, height:Int, verticalOffset:Int, zOrder:Int):MarkerTickPartsInfo {
        return MarkerTickPartsInfo(color,width,height,verticalOffset,zOrder).apply { markerTickPartsInfo = this }
    }

    // endregion

    init {
        StyledAttrRetriever(context, attrs, R.styleable.PlayerSlider, defStyleAttr, 0).use { sar ->
            try {
                setThumbAttrs(sar)
                setMarkerAttrs(sar)

                setRailLeftAttrs(
                    color = sar.getColor(R.styleable.PlayerSlider_ampRailLeftColor, com.google.android.material.R.attr.colorPrimaryFixed, com.google.android.material.R.attr.colorPrimary, Color.BLUE),
                    height = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRailLeftHeight, DEF_RAIL_HEIGHT.dp),
                    verticalOffset = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRailLeftVerticalOffset, (-DEF_RAIL_HEIGHT/2).dp),
                    zOrder = sar.sa.getInt(R.styleable.PlayerSlider_ampRailLeftZOrder, 1)
                )
                setRailRightAttrs(
                    color = sar.getColor(R.styleable.PlayerSlider_ampRailRightColor, com.google.android.material.R.attr.colorOnPrimaryFixedVariant, com.google.android.material.R.attr.colorPrimaryVariant, Color.DKGRAY),
                    height = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRailRightHeight,DEF_RAIL_HEIGHT.dp),
                    verticalOffset = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRailRightVerticalOffset,(-DEF_RAIL_HEIGHT / 2).dp),
                    zOrder = sar.sa.getInt(R.styleable.PlayerSlider_ampRailRightZOrder, 1)
                )
                setEnabledChapterAttrs(
                    color = sar.getColor(R.styleable.PlayerSlider_ampRangeEnabledColor,com.google.android.material.R.attr.colorSecondaryFixedDim, com.google.android.material.R.attr.colorSecondary, Color.GREEN),
                    height = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRangeEnabledHeight, DEF_ENABLED_RANGE_HEIGHT.dp),
                    verticalOffset = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRangeEnabledVerticalOffset, (-DEF_ENABLED_RANGE_HEIGHT / 2).dp),
                    zOrder = sar.sa.getInt(R.styleable.PlayerSlider_ampRangeEnabledZOrder, 0)
                )
                setDisabledChapterAttrs(
                    color = sar.getColor(R.styleable.PlayerSlider_ampRangeDisabledColor,com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF808080.toInt()),
                    height = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRangeDisabledHeight,DEF_RAIL_HEIGHT.dp),
                    verticalOffset = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRangeDisabledVerticalOffset,(-DEF_RAIL_HEIGHT / 2).dp),
                    zOrder = sar.sa.getInt(R.styleable.PlayerSlider_ampRangeDisabledZOrder, 2)
                )
                setMarkerTickAttrs(
                    color = sar.getColor(R.styleable.PlayerSlider_ampRangeTickColor, com.google.android.material.R.attr.colorOnSurface, Color.BLACK),
                    width = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRangeTickWidth, DEF_MARKER_TICK_WIDTH.dp),
                    height = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRangeTickHeight, DEF_MARKER_TICK_HEIGHT.dp),
                    verticalOffset = sar.getDimensionPixelSize(R.styleable.PlayerSlider_ampRangeTickVerticalOffset, (-DEF_ENABLED_RANGE_HEIGHT / 2).dp),
                    zOrder = sar.sa.getInt(R.styleable.PlayerSlider_ampRangeTickZOrder, 4)
                )

                showChapterBar = sar.sa.getBoolean(R.styleable.PlayerSlider_ampShowChapterBar, true)
                updateDrawableParts()
            } catch (e: Throwable) {
                logger.error(e)
                throw e
            }
        }
    }


    // 位置・サイズ
    private val upperHeight:Int = drawingParts.maxOfOrNull { -it.verticalOffset } ?: 0
    private val lowerHeight:Int = drawingParts.maxOfOrNull { it.verticalOffset + it.height } ?: 0
    private val allOverHeight:Int = lowerHeight+upperHeight
    private val leftMargin:Float = max(thumbPartsInfo.horizontalCenter, markerPartsInfo.horizontalCenter)
    private val rightMargin:Float = max(thumbPartsInfo.width-thumbPartsInfo.horizontalCenter, markerPartsInfo.width-markerPartsInfo.horizontalCenter)
    private val horizontalMargin:Float = leftMargin + rightMargin

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
        if(duration==0L) return
        for(p in drawingParts) {
            p.draw(canvas)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handleTouchEvent(event.action, event.x, event.y)
    }

    private var dragging = false
    private fun handleTouchEvent(action:Int, x:Float, @Suppress("UNUSED_PARAMETER") y:Float):Boolean {
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
        position = max(0, min(duration, xToPosition(x)))
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