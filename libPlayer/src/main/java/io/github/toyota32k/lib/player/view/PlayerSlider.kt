package io.github.toyota32k.lib.player.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.getColorOrThrow
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import io.github.toyota32k.binder.BaseBinding
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.getAttrColor
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.LifecycleDisposer
import io.github.toyota32k.utils.RGB
import io.github.toyota32k.utils.asMutableLiveData
import io.github.toyota32k.utils.disposableObserve
import io.github.toyota32k.utils.dp2px
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
        const val DEF_NARKER_ICON_HEIGHT = 10
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

    fun positionToX(position:Long):Float {
        return (position.toFloat() / duration.toFloat()) * sliderRange + leftMargin
    }
    fun xToPosition(x:Float):Long {
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
    private val allParts get() = { listOf(thumbPartsInfo, markerPartsInfo, railRightInfo, railLeftInfo, enableedChapterInfo, disabledChapterInfo, markerTickPartsInfo) }
    private var drawingParts:List<IPartsInfo> = emptyList()
    private fun updateDrawableParts() {
        drawingParts = allParts().filter { it.isValid }.sortedBy { it.zOrder }
    }
    // endregion

    // region Icon Parts
    abstract inner class IconPartsInfo(val drawable:Drawable, override val verticalOffset:Int, val width:Int, override val height:Int, val horizontalCenter: Float) : IPartsInfo {
        val top:Int get() =  sliderTop + upperHeight + verticalOffset
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
    inner class ThumbPartsInfo(drawable:Drawable, verticalOffset:Int, width:Int, height:Int, horizontalCenter: Float): IconPartsInfo(drawable, verticalOffset, width, height, horizontalCenter) {
        override val description: String = "Thumb"
        override val zOrder: Int = Int.MAX_VALUE
        override fun draw(canvas: Canvas) {
            drawAt(canvas, position)
        }
    }
    private lateinit var thumbPartsInfo:ThumbPartsInfo
    private fun getDefaultThumbDrawable(context:Context):Drawable = AppCompatResources.getDrawable(context, R.drawable.ic_player_slider_thumb)!!

    private fun <T:IconPartsInfo> createIconPartsInfo(dr: Drawable, width: Int, height: Int, horizontalCenter: Float, creator:(Int, Int, Float)->T) : T {
        val w = if (width<0) dr.intrinsicWidth else width
        val h = if (height<0) dr.intrinsicHeight else height
        val center = if (horizontalCenter<0) w.toFloat()/2f else horizontalCenter
        return creator(w, h, center)
    }

    private fun setThumbAttrs(drawable: Drawable?, verticalOffset: Int, width: Int, height: Int, horizontalCenter: Float, tintColor:Int) :ThumbPartsInfo {
        val dr = drawable ?: getDefaultThumbDrawable(context)
        if(tintColor != 0) {
            dr.setTint(tintColor)
        }
        return createIconPartsInfo(dr, width, height, horizontalCenter) { w,h,center ->
            ThumbPartsInfo(dr, verticalOffset, w, h, center)
        }.apply { thumbPartsInfo = this }
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
    private fun setMarkerAttrs(drawable: Drawable?, verticalOffset: Int, width: Int, height: Int, horizontalCenter: Float, tintColor:Int, zOrder: Int) :MarkerPartsInfo {
        val dr = drawable ?: getDefaultMarkerDrawable(context)
        if(tintColor != 0) {
            dr.setTint(tintColor)
        }
        return createIconPartsInfo(dr, width, height, horizontalCenter) {w,h,center ->
            MarkerPartsInfo(dr, verticalOffset, w, h, center, zOrder)
        }.apply { markerPartsInfo = this }
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
        val top:Int get() =  sliderTop + upperHeight + verticalOffset
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
    fun Int.formatColor():String {
        return String.format("#%08X", this)
    }
    fun getColor(sa: TypedArray,index:Int, def:Int):Int {
        return try {
            sa.getColorOrThrow(index)
        } catch(e:Throwable) {
            def
        }
    }

    init {
        val sa = context.theme.obtainStyledAttributes(attrs, R.styleable.PlayerSlider, defStyleAttr, 0)
        try {
            val surfaceColor = context.theme.getAttrColor(com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF.toInt())
            val primaryColor = context.theme.getAttrColor(
                com.google.android.material.R.attr.colorPrimary,
                0xFFff80ab.toInt()
            )
            var primaryDarkColor = context.theme.getAttrColor(com.google.android.material.R.attr.colorPrimaryVariant, 0xFFc94f7c.toInt())
            if (primaryDarkColor == primaryColor) {
                primaryDarkColor = RGB.brend(primaryColor, surfaceColor)
            }

            val secondaryColor = context.theme.getAttrColor(com.google.android.material.R.attr.colorSecondary, 0xFFaeea00.toInt())
            val disabledColor = RGB.brend(0xFF808080.toInt(), surfaceColor)
            val textColor = context.theme.getAttrColor(com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt())
//            val accentColor = context.theme.getAttrColor(com.google.android.material.R.attr.colorAccent, 0xFF69F0AE.toInt())
            val accentColor = context.theme.getAttrColor(com.google.android.material.R.attr.colorTertiary, 0xFF69F0AE.toInt())

            logger.debug("primaryColor=${primaryColor.formatColor()}, primaryDarkColor=${primaryDarkColor.formatColor()}, secondaryColor=${secondaryColor.formatColor()}, disabledColor=${disabledColor.formatColor()}, textColor=${textColor.formatColor()}, accentColor=${accentColor.formatColor()}")
//            val primaryColor = 0xFFFF0000.toInt()
//            val primaryDarkColor = 0xFF00FF00.toInt()
//            val secondaryColor = 0xFF0000FF.toInt()
//            val disabledColor = 0xFF808080.toInt()
//            val baseColor = 0xFF000000.toInt()

            setThumbAttrs(
                drawable = sa.getDrawable(R.styleable.PlayerSlider_thumb) ?: getDefaultThumbDrawable(context),
                verticalOffset = sa.getDimensionPixelSize(R.styleable.PlayerSlider_thumbVerticalOffset, -context.dp2px(DEF_ENABLED_RANGE_HEIGHT / 2)),
                width = sa.getDimensionPixelSize(R.styleable.PlayerSlider_thumbIconWidth, -1),
                height = sa.getDimensionPixelSize(R.styleable.PlayerSlider_thumbIconHeight, context.dp2px(DEF_ENABLED_RANGE_HEIGHT)),
                horizontalCenter = sa.getDimensionPixelSize(R.styleable.PlayerSlider_thumbHorizontalCenter, -1).toFloat(),
                tintColor = sa.getColor(R.styleable.PlayerSlider_thumbTintColor, accentColor)
            )
            setMarkerAttrs(
                drawable = sa.getDrawable(R.styleable.PlayerSlider_marker),
                verticalOffset = sa.getDimensionPixelSize(R.styleable.PlayerSlider_markerVerticalOffset, context.dp2px(DEF_RAIL_HEIGHT / 2)),
                width = sa.getDimensionPixelSize(R.styleable.PlayerSlider_markerIconWidth, context.dp2px(DEF_NARKER_ICON_HEIGHT)),
                height = sa.getDimensionPixelSize(R.styleable.PlayerSlider_markerIconHeight, context.dp2px(DEF_NARKER_ICON_HEIGHT)),
                horizontalCenter = sa.getDimensionPixelSize(R.styleable.PlayerSlider_markerHorizontalCenter, -1).toFloat(),
                tintColor = sa.getColor(R.styleable.PlayerSlider_markerTintColor, textColor),
                zOrder = sa.getInt(R.styleable.PlayerSlider_markerZOrder, 3)
            )

            setRailRightAttrs(
                color = sa.getColor(R.styleable.PlayerSlider_railRightColor, primaryDarkColor),
                height = sa.getDimensionPixelSize(R.styleable.PlayerSlider_railRightHeight, context.dp2px(DEF_RAIL_HEIGHT)),
                verticalOffset = sa.getDimensionPixelSize(R.styleable.PlayerSlider_railRightVerticalOffset, -context.dp2px(DEF_RAIL_HEIGHT / 2)),
                zOrder = sa.getInt(R.styleable.PlayerSlider_railRightZOrder, 1)
            )
            setRailLeftAttrs(
                color = sa.getColor(R.styleable.PlayerSlider_railLeftColor, primaryColor),
                height = sa.getDimensionPixelSize(R.styleable.PlayerSlider_railLeftHeight, context.dp2px(DEF_RAIL_HEIGHT)),
                verticalOffset = sa.getDimensionPixelSize(R.styleable.PlayerSlider_railLeftVerticalOffset, -context.dp2px(DEF_RAIL_HEIGHT / 2)),
                zOrder = sa.getInt(R.styleable.PlayerSlider_railLeftZOrder, 1)
            )
            setEnabledChapterAttrs(
                color = sa.getColor(R.styleable.PlayerSlider_rangeEnabledColor, secondaryColor),
                height = sa.getDimensionPixelSize(R.styleable.PlayerSlider_rangeEnabledHeight, context.dp2px(DEF_ENABLED_RANGE_HEIGHT)),
                verticalOffset = sa.getDimensionPixelSize(R.styleable.PlayerSlider_rangeEnabledVerticalOffset, -context.dp2px(DEF_ENABLED_RANGE_HEIGHT / 2)),
                zOrder = sa.getInt(R.styleable.PlayerSlider_rangeEnabledZOrder, 0)
            )
            setDisabledChapterAttrs(
                color = sa.getColor(R.styleable.PlayerSlider_rangeDisabledColor, disabledColor),
                height = sa.getDimensionPixelSize(R.styleable.PlayerSlider_rangeDisabledHeight, context.dp2px(DEF_RAIL_HEIGHT)),
                verticalOffset = sa.getDimensionPixelSize(R.styleable.PlayerSlider_rangeDisabledVerticalOffset, -context.dp2px(DEF_RAIL_HEIGHT / 2)),
                zOrder = sa.getInt(R.styleable.PlayerSlider_rangeDisabledZOrder, 2)
            )
            setMarkerTickAttrs(
                color = sa.getColor(R.styleable.PlayerSlider_rangeTickColor, textColor),
                width = sa.getDimensionPixelSize(R.styleable.PlayerSlider_rangeTickWidth, context.dp2px(DEF_MARKER_TICK_WIDTH)),
                height = sa.getDimensionPixelSize(R.styleable.PlayerSlider_rangeTickHeight, context.dp2px(DEF_MARKER_TICK_HEIGHT)),
                verticalOffset = sa.getDimensionPixelSize(R.styleable.PlayerSlider_rangeTickVerticalOffset, -context.dp2px(DEF_ENABLED_RANGE_HEIGHT / 2)),
                zOrder = sa.getInt(R.styleable.PlayerSlider_rangeTickZOrder, 4)
            )

            showChapterBar = sa.getBoolean(R.styleable.PlayerSlider_showChapterBar, true)
            updateDrawableParts()
        } catch (e:Throwable) {
            logger.error(e)
            throw e
        } finally {
            sa.recycle()
        }
    }


    // 位置・サイズ
    private val upperHeight:Int = drawingParts.map {  -it.verticalOffset }.max()
    private val lowerHeight:Int = drawingParts.map { it.verticalOffset+it.height }.max()
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


        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)
        val height = when(heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST-> Math.min(allOverHeight, heightSize)
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

    inner class DraggingInfo {
        fun tapAt(x:Float) {
            dragging = true
        }
        fun moveTo(x:Float) {
            if(dragging) {
                position = max(0, min(duration, xToPosition(x)))
            }
        }

    }

    var dragging = false
    private fun handleTouchEvent(action:Int, x:Float, y:Float):Boolean {
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
        val slider:PlayerSlider? get() = view as? PlayerSlider
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

        private fun clipByRange(a:Float, b:Float, v:Float):Float {
            val min = java.lang.Float.min(a, b)
            val max = java.lang.Float.max(a, b)
            return java.lang.Float.min(java.lang.Float.max(min, v), max)
        }

        private fun fitToStep(v:Float, s:Float):Float {
            return if(s==0f) {
                v
            } else {
                @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
                s*Math.round(v/s)
            }
        }

        override fun onDataChanged(v: Long?) {
            if(v!=null) {
                slider?.setPositionNotNotify(v)
            }
        }
    }
}

fun Binder.playerSliderBinding(slider: PlayerSlider, data: MutableStateFlow<Long>, duration: Flow<Long>? = null):Binder {
    add(PlayerSlider.Binding(BindingMode.TwoWay, data.asMutableLiveData(requireOwner), duration?.asLiveData()).apply { connect(requireOwner, slider) })
    return this
}
fun Binder.playerSliderBinding(slider: PlayerSlider, data: Flow<Long>, duration: Flow<Long>? = null): Binder {
    add(PlayerSlider.Binding(BindingMode.OneWay, data.asLiveData(), duration?.asLiveData()).apply { connect(requireOwner, slider) })
    return this
}