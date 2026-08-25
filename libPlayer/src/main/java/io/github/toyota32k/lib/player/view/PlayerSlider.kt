package io.github.toyota32k.lib.player.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
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
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.android.StyledAttrRetriever
import io.github.toyota32k.utils.android.dp
import io.github.toyota32k.utils.android.lifecycleOwner
import io.github.toyota32k.utils.android.px
import io.github.toyota32k.utils.lifecycle.LifecycleDisposer
import io.github.toyota32k.utils.lifecycle.asMutableLiveData
import io.github.toyota32k.utils.lifecycle.disposableObserve
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class PlayerSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = TpLib.logger
        const val DEF_RAIL_HEIGHT = 6f
        const val DEF_ENABLED_RANGE_HEIGHT = 0f
        const val DEF_DISABLED_RANGE_HEIGHT = 1f
        const val DEF_UNDER_THUMB_OUTER_WIDTH = 3f
        const val DEF_UNDER_THUMB_INNER_WIDTH = 1f
        const val DEF_MARKER_TICK_WIDTH = 1f
        const val DEF_MARKER_ICON_HEIGHT = 10f
        const val DEF_MARKER_ICON_WIDTH = 5f
        const val DEF_RAIL_MARGIN_START = 5f
        const val DEF_RAIL_MARGIN_END = 5f
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
    private var mPlayRange: Range? = null
//    val range:Range get() = mPlayRange ?: Range.empty

    private fun clampPosition(position: Long):Long {
        return position.coerceIn(startPosition, endPosition.coerceAtLeast(startPosition))
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
        (markerPartsInfo as? MarkerPartsInfo)?.setChapterList(chapterList)

        needCanvasLayer = false
        val enabledRanges = chapterList?.enabledRanges() ?: emptyList()
        val disabledRanges = chapterList?.disabledRanges() ?: emptyList()
        val eci = enabledChapterInfo as? ChapterPartsInfo
        if (eci!=null) {
            eci.setRanges(enabledRanges)
            if (mRailBaseColor!=0 && eci.eraseRailBase && enabledRanges.isNotEmpty()) {
                needCanvasLayer = true
            }
        }
        val dci = disabledChapterInfo as? ChapterPartsInfo
        if (dci!=null) {
            dci.setRanges(disabledRanges)
            if (mRailBaseColor!=0 && dci.eraseRailBase && disabledRanges.isNotEmpty()) {
                needCanvasLayer = true
            }
        }
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
        val verticalOffset:Float
        val height:Float
        val zOrder:Int

        val isValid:Boolean get() = height > 0
//        val hasOffset:Boolean get() = verticalOffset != Float.MIN_VALUE
        fun draw(canvas: Canvas)
    }
    interface IIconPartsInfo: IPartsInfo {
        val width: Float
        val horizontalCenter: Float
    }

    private object EmptyPart : IIconPartsInfo {
        override val description: String = "empty"
        override val verticalOffset: Float = 0f
        override val height: Float = 0f
        override val zOrder: Int = 0
        override val width: Float = 0f
        override val horizontalCenter: Float = 0f
        override fun draw(canvas: Canvas) {}
    }

    private enum class Parts(val zOrder:Int) {
        RailLeft(20),
        RailRight(30),
        EnabledChapter(10),
        DisabledChapter(40),
        Marker(50),

        MarkerTick(1000),
        UnderThumbOuter(1100),
        UnderThumbInner(1200),
        Thumb(2000),
    }

    private val allParts get() = listOf(thumbPartsInfo, underThumbOuterInfo, underThumbInnerInfo, markerPartsInfo, railRightInfo, railLeftInfo, enabledChapterInfo, disabledChapterInfo, markerTickPartsInfo)
    private val railParts get() = listOf(railRightInfo, railLeftInfo, enabledChapterInfo, disabledChapterInfo)
    private var drawingParts:List<IPartsInfo> = emptyList()
    private fun updateDrawableParts() {
        drawingParts = allParts.filter { it.isValid }.sortedBy { it.zOrder }
    }
    data class VerticalPosition(var top:Float, var bottom:Float) {
        val height:Float get() = bottom - top
        val upperHeight:Float get() = -top        // BaseLineより上の高さ
//        val lowerHeight get() = bottom      // BaseLineより下の高さ
        val center:Float get() = top + height/2
    }

    private fun getOutlinePosition(parts:List<IPartsInfo>):VerticalPosition {
        return parts.fold(VerticalPosition(Float.MAX_VALUE,0f)) { acc, p ->
            acc.apply {
                top = min(top, p.verticalOffset-p.height/2)
                bottom = max( bottom, p.verticalOffset+p.height/2)
            }
        }
    }
    private var mRailBaseHeight: Float = 0f // px
    var mRailBaseColor: Int = 0
    private var mRailBasePaint: Paint? = null
    private val railBasePaint: Paint get() =
        mRailBasePaint ?: Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = mRailBaseHeight
            if (mRailBaseColor != 0) {
                color = mRailBaseColor
            } else {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            mRailBasePaint = this
        }

    private var mRailOutline: VerticalPosition? = null
    private fun railOutline():VerticalPosition {
        return mRailOutline ?: getOutlinePosition(railParts).apply { mRailOutline = this }
    }
    private var mAllOverOutline: VerticalPosition? = null
    private fun allOverOutline(): VerticalPosition {
        return mAllOverOutline ?: getOutlinePosition(drawingParts).apply { mAllOverOutline = this }
    }
    private fun getYCenter(verticalOffset:Float):Float {
        return upperMargin + allOverOutline().upperHeight + verticalOffset
    }
    private fun getYTop(verticalOffset:Float, height:Float):Float {
        return getYCenter(verticalOffset) - height / 2f
    }

    // endregion

    // region Icon Parts
    abstract inner class IconPartsInfo(val drawable:Drawable?, override val verticalOffset: Float, override val width: Float, override val height: Float, override val horizontalCenter: Float) : IIconPartsInfo {
        private val mTop:Float =  getYTop(verticalOffset,height)
        protected fun drawAt(canvas:Canvas, p:Long) {
            if(drawable!=null) {
                val left = positionToX(p) - horizontalCenter
                val top = getYTop(verticalOffset,height)
                drawable.setBounds(left.roundToInt(), top.roundToInt(), (left + width).roundToInt(), (top + height).roundToInt())
                drawable.draw(canvas)
            }
        }
    }

    /**
     * Thumb アイコン
     */
    inner class ThumbPartsInfo(
        drawable:Drawable?, verticalOffset: Float, width: Float, height: Float, horizontalCenter: Float, override val zOrder: Int): IconPartsInfo(drawable, verticalOffset, width, height, horizontalCenter) {
        override val description: String = "Thumb"

        override fun draw(canvas: Canvas) {
            drawAt(canvas, position)
        }
    }
    private var thumbPartsInfo:IIconPartsInfo = EmptyPart
//    private fun getDefaultThumbDrawable(context:Context):Drawable = AppCompatResources.getDrawable(context, R.drawable.ic_player_slider_thumb)!!


    private var underThumbOuterInfo: IPartsInfo = EmptyPart
    private var underThumbInnerInfo: IPartsInfo = EmptyPart

    private fun setThumbAttrs(sar: StyledAttrRetriever) {
        val drawable = sar.getDrawable(R.styleable.ControlPanel_ampThumbIcon)
        thumbPartsInfo = if (drawable!=null) {
            val w = sar.getDimension(R.styleable.ControlPanel_ampThumbIconWidth, drawable.intrinsicWidth.px)
            val h = sar.getDimension(R.styleable.ControlPanel_ampThumbIconHeight, drawable.intrinsicHeight.px)
            val verticalOffset = sar.getDimension(R.styleable.ControlPanel_ampThumbVerticalOffset, (-(mRailBaseHeight+h)/2).px)
            val horizontalCenter = sar.getDimension(R.styleable.ControlPanel_ampThumbHorizontalCenter, (w/2f).px)
            val tintColor = sar.sa.getColor(R.styleable.ControlPanel_ampThumbTintColor, 0)
            if(tintColor != 0) {
                drawable.setTint(tintColor)
            }
            ThumbPartsInfo(drawable, verticalOffset, w, h, horizontalCenter, Parts.Thumb.zOrder)
        } else {
            EmptyPart
        }
        val railOutline = railOutline()
        val verticalOffset = sar.getDimension(R.styleable.ControlPanel_ampUnderThumbVerticalOffset, railOutline.center.px)
        val height = sar.getDimension(R.styleable.ControlPanel_ampUnderThumbHeight, railOutline.height.px)
        val widthOuter = sar.getDimension(R.styleable.ControlPanel_ampUnderThumbOuterWidth, DEF_UNDER_THUMB_OUTER_WIDTH.dp)
        val widthInner = sar.getDimension(R.styleable.ControlPanel_ampUnderThumbInnerWidth, DEF_UNDER_THUMB_INNER_WIDTH.dp)
        val colorInner = sar.getColor(R.styleable.ControlPanel_ampUnderThumbInnerColor, com.google.android.material.R.attr.colorPrimaryFixed, 0xFF000000.toInt())
        val colorOuter = sar.getColor(R.styleable.ControlPanel_ampUnderThumbOuterColor, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF.toInt())

        underThumbOuterInfo =if (widthOuter>0) {
            UnderThumbPartsInfo(colorOuter, widthOuter, height, verticalOffset, Parts.UnderThumbOuter.zOrder)
        } else {
            EmptyPart
        }
        underThumbInnerInfo = if (widthInner>0) {
            UnderThumbPartsInfo(colorInner, widthInner, height, verticalOffset, Parts.UnderThumbInner.zOrder)
        } else {
            EmptyPart
        }
    }

    /**
     * UnderThumb Line
     */
    inner class UnderThumbPartsInfo(
        @ColorInt color: Int,
        val width: Float,
        height: Float,
        verticalOffset: Float,
        zOrder:Int) : RangePartsInfo("UnderThumb($width)", color,height,verticalOffset,zOrder) {
        override fun draw(canvas: Canvas) {
            val d = width/2
            val sx = positionToX(position) - d
            val ex = sx + width
            val y = yCenter
            canvas.drawLine(sx,y,ex,y,paint)
        }
    }


    /**
     * Marker Icon
     */
    inner class MarkerPartsInfo(drawable: Drawable?, verticalOffset: Float, width: Float, height: Float, horizontalCenter: Float, override val zOrder: Int) : IconPartsInfo(drawable, verticalOffset, width, height, horizontalCenter) {
        override val description: String = "Marker"

        var markers:List<Long> = emptyList()
            private set
        override val isValid: Boolean
            get() = height>0 && showChapterBar

        fun setChapterList(chapterList: IChapterList?) {
            markers = chapterList?.chapters?.drop(1)?.map { it.position } ?: emptyList()
        }

        override fun draw(canvas: Canvas) {
            for(p in markers) {
                drawAt(canvas, p)
            }
        }
    }
    var markerPartsInfo: IIconPartsInfo = EmptyPart
    private fun getDefaultMarkerDrawable(context:Context):Drawable = AppCompatResources.getDrawable(context, R.drawable.ic_player_slider_marker)!!
    private fun setMarkerAttrs(sar:StyledAttrRetriever) {
        val customIcon = sar.getDrawable(R.styleable.ControlPanel_ampMarkerIcon)
        val drawable = customIcon ?: if (sar.sa.getBoolean(R.styleable.ControlPanel_ampMarkerUseDefaultIcon, true)) getDefaultMarkerDrawable(context) else null
        markerPartsInfo = if (drawable == null) {
            // markerを描画しない
            // ただし、MarkerTick が、markers: List<Long> を使うので、EmptyPart ではなく、空の MarkerPartsInfo を返す
            MarkerPartsInfo(null, 0f, 0f, 0f, 0f, 0)
        } else {
            val outlineRail = railOutline()
            val w = sar.getDimension(R.styleable.ControlPanel_ampMarkerIconWidth, DEF_MARKER_ICON_WIDTH.dp)
            val h = sar.getDimension(R.styleable.ControlPanel_ampMarkerIconHeight, DEF_MARKER_ICON_HEIGHT.dp)
            val verticalOffset = sar.getDimension(R.styleable.ControlPanel_ampMarkerVerticalOffset, (outlineRail.bottom + h/2f).px)
            val horizontalCenter = sar.getDimension(R.styleable.ControlPanel_ampMarkerHorizontalCenter, (w / 2f).px)
            val tintColor = sar.getColor(R.styleable.ControlPanel_ampMarkerTintColor, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF000000.toInt())
            val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampMarkerZOrder, Parts.Marker.zOrder)
            if (tintColor != 0) {
                drawable.setTint(tintColor)
            }
            MarkerPartsInfo(drawable, verticalOffset, w, h, horizontalCenter, zOrder)
        }
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

    abstract inner class RangePartsInfo(
        override val description: String, val paint: Paint,
        final override val height: Float, override val verticalOffset: Float, override val zOrder:Int) : IPartsInfo {
        constructor(description:String, @ColorInt color:Int, height: Float, verticalOffset: Float, zOrder:Int) : this(description, paintOfColor(color), height, verticalOffset, zOrder)
        init {
            paint.strokeWidth = height
        }
//        private val top: Float get() =  sliderTop + upperHeight + verticalOffset
        val yCenter:Float get() = getYCenter(verticalOffset)

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
        height: Float,
        verticalOffset: Float,
        zOrder:Int
    ) : RangePartsInfo("RailRight",color,height,verticalOffset,zOrder) {
        override fun draw(canvas: Canvas) {
            drawRange(canvas, position, endPosition)
        }
    }
    var railRightInfo:IPartsInfo = EmptyPart

    private fun setRailRightAttrs(sar: StyledAttrRetriever) :RailRightInfo {
        val color = sar.getColor(R.styleable.ControlPanel_ampRailRightColor, com.google.android.material.R.attr.colorOnPrimaryFixedVariant, com.google.android.material.R.attr.colorPrimaryVariant, Color.DKGRAY)
        val height = sar.getDimension(R.styleable.ControlPanel_ampRailRightHeight,mRailBaseHeight.px)
        val verticalOffset = sar.sa.getDimension(R.styleable.ControlPanel_ampRailRightVerticalOffset,0f)
        val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampRailRightZOrder, Parts.RailRight.zOrder)
        return RailRightInfo(color,height,verticalOffset,zOrder).apply { railRightInfo = this }
    }

    inner class RailLeftInfo(
        @ColorInt color: Int,
        height: Float,
        verticalOffset: Float,
        zOrder:Int
    ) : RangePartsInfo("RailLeft", color,height,verticalOffset,zOrder) {
        override fun draw(canvas: Canvas) {
            drawRange(canvas, 0, position)
        }
    }
    var railLeftInfo:IPartsInfo = EmptyPart

    private fun setRailLeftAttrs(sar:StyledAttrRetriever) :RailLeftInfo {
        val color = sar.getColor(R.styleable.ControlPanel_ampRailLeftColor, com.google.android.material.R.attr.colorPrimaryFixed, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
        val height = sar.getDimension(R.styleable.ControlPanel_ampRailLeftHeight, mRailBaseHeight.px)
        val verticalOffset = sar.sa.getDimension(R.styleable.ControlPanel_ampRailLeftVerticalOffset, 0f)
        val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampRailLeftZOrder, Parts.RailLeft.zOrder)
        return RailLeftInfo(color,height,verticalOffset,zOrder).apply { railLeftInfo = this }
    }

    class CanvasLayer : AutoCloseable {
        private var canvas: Canvas? = null
        private var layer: Int = 0
        fun open(canvas: Canvas, sx: Float, sy: Float, w: Float, h: Float, paint: Paint?=null) {
            this.canvas = canvas
            layer = canvas.saveLayer(sx, sy, w, h, paint)
        }
        override fun close() {
            canvas?.restoreToCount(layer)
            canvas = null
        }
        fun reset() {
            canvas = null
            layer = 0
        }
    }

    var showChapterBar = true
    inner class ChapterPartsInfo(
        description: String,
        @ColorInt color: Int,
        height: Float,
        verticalOffset: Float,
        zOrder:Int,
        val eraseRailBase: Boolean
    ) : RangePartsInfo(description, color,height,verticalOffset,zOrder) {
        private var ranges:List<Range> = emptyList()
        override val isValid: Boolean
            get() = height>0 && showChapterBar

        val rangeCount:Int get() = ranges.size

        fun setRanges(ranges:List<Range>) {
            this.ranges = ranges
        }

        fun eraseRange(canvas: Canvas, start:Long, end:Long) {
            if (!eraseRailBase) return
            if(end<=startPosition) return
            if(endPosition<=start) return
            if(end<=start) return

            val ex = positionToX(clampPosition(end))
            val sx = positionToX(clampPosition(start))
            val y = getYCenter(0f)
            canvas.drawLine(sx, y, ex, y, railBasePaint)
        }

        override fun draw(canvas: Canvas) {
            for(r in ranges) {
                val start = r.start
                val end = if(r.end==0L) naturalDuration else r.end
                eraseRange(canvas, start, end)
                drawRange(canvas, start, end)
            }
        }
    }

    var disabledChapterInfo:IPartsInfo = EmptyPart
    var enabledChapterInfo:IPartsInfo = EmptyPart

    private fun setEnabledChapterAttrs(sar: StyledAttrRetriever) :ChapterPartsInfo {
        val color = sar.getColor(R.styleable.ControlPanel_ampRangeEnabledColor,com.google.android.material.R.attr.colorSecondaryFixedDim, com.google.android.material.R.attr.colorSecondary, Color.GREEN)
        val height = sar.getDimension(R.styleable.ControlPanel_ampRangeEnabledHeight, DEF_ENABLED_RANGE_HEIGHT.dp)
        val verticalOffset = sar.getDimension(R.styleable.ControlPanel_ampRangeEnabledVerticalOffset, (DEF_ENABLED_RANGE_HEIGHT / 2).dp - height.px)
        val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampRangeEnabledZOrder, Parts.EnabledChapter.zOrder)
        val eraseRailBase = sar.sa.getBoolean(R.styleable.ControlPanel_ampRangeEnabledEraseRail, false)
        return ChapterPartsInfo("EnabledChapters", color,height,verticalOffset,zOrder, eraseRailBase)
    }

    private fun setDisabledChapterAttrs(sar: StyledAttrRetriever) :ChapterPartsInfo {
        val color = sar.getColor(R.styleable.ControlPanel_ampRangeDisabledColor,com.google.android.material.R.attr.colorOutline, 0xFF808080.toInt())
        val height = sar.getDimension(R.styleable.ControlPanel_ampRangeDisabledHeight, DEF_DISABLED_RANGE_HEIGHT.dp)
        val verticalOffset = sar.sa.getDimension(R.styleable.ControlPanel_ampRangeDisabledVerticalOffset, 0f)
        val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampRangeDisabledZOrder, Parts.DisabledChapter.zOrder)
        val eraseRailBase = sar.sa.getBoolean(R.styleable.ControlPanel_ampRangeDisabledEraseRail, true)
        return ChapterPartsInfo("DisabledChapters",color,height,verticalOffset,zOrder, eraseRailBase).apply { disabledChapterInfo = this }
    }

    inner class MarkerTickPartsInfo(
        @ColorInt color: Int,
        val width: Float,
        height: Float,
        verticalOffset: Float,
        zOrder:Int) : RangePartsInfo("MarkerTick", color,height,verticalOffset,zOrder) {
        override val isValid: Boolean
            get() = height>0 && showChapterBar

        override fun draw(canvas: Canvas) {
            val markerParts = markerPartsInfo as? MarkerPartsInfo ?: return
            val d = width/2
            for(p in markerParts.markers) {
                val sx = positionToX(p) - d
                val ex = sx + width
                val y = yCenter
                canvas.drawLine(sx,y,ex,y,paint)
            }
        }
    }
    var markerTickPartsInfo:IPartsInfo = EmptyPart

    private fun setMarkerTickAttrs(sar: StyledAttrRetriever):MarkerTickPartsInfo {
        val railOutline = railOutline()
        val color = sar.getColor(R.styleable.ControlPanel_ampRangeTickColor, com.google.android.material.R.attr.colorOutline, Color.BLACK)
        val width = sar.getDimension(R.styleable.ControlPanel_ampRangeTickWidth, DEF_MARKER_TICK_WIDTH.dp)
        val height = sar.getDimension(R.styleable.ControlPanel_ampRangeTickHeight, railOutline.height.px)
        val verticalOffset = sar.getDimension(R.styleable.ControlPanel_ampRangeTickVerticalOffset, railOutline.center.px)
        val zOrder = sar.sa.getInt(R.styleable.ControlPanel_ampRangeTickZOrder, Parts.MarkerTick.zOrder)
        return MarkerTickPartsInfo(color,width,height,verticalOffset,zOrder).apply { markerTickPartsInfo = this }
    }

    // endregion
    fun setPlayerSliderAttributes(sar: StyledAttrRetriever, reLayout:Boolean=true) {
        if (sar.sa.getBoolean(R.styleable.ControlPanel_ampAttrsByParent, true)) {
            mRailOutline = null     // 要再計算
            mAllOverOutline = null  // 要再計算
            mRailBasePaint = null
            mRailBaseHeight = sar.getDimension(R.styleable.ControlPanel_ampRailBaseHeight, DEF_RAIL_HEIGHT.dp)
            mRailBaseColor = sar.sa.getColor(R.styleable.ControlPanel_ampRailBaseColor, 0)
            setRailLeftAttrs(sar)
            setRailRightAttrs(sar)
            setEnabledChapterAttrs(sar)
            setDisabledChapterAttrs(sar)
            setThumbAttrs(sar)
            setMarkerAttrs(sar)
            setMarkerTickAttrs(sar)
            showChapterBar = sar.sa.getBoolean(R.styleable.ControlPanel_ampShowChapterBar, true)
            updateDrawableParts()
            staticMarginLeft = sar.getDimension(R.styleable.ControlPanel_ampRailMarginStart, DEF_RAIL_MARGIN_START.dp)
            staticMarginRight = sar.getDimension(R.styleable.ControlPanel_ampRailMarginEnd, DEF_RAIL_MARGIN_END.dp)
            calcLayoutBasis()
            if(reLayout) {
                requestLayout()
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
    private var staticMarginLeft: Float = 0f
    private var staticMarginRight: Float = 0f


    // 位置・サイズ
//    private var upperHeight: Float = 0f
//    private var lowerHeight: Float = 0f
//    private var allOverHeight: Float = 0f
    private var leftMargin:Float = 0f
    private var rightMargin:Float = 0f
    private var horizontalMargin:Float = 0f
    private var needCanvasLayer: Boolean = false

    private fun calcLayoutBasis() {
//        upperHeight = drawingParts.maxOfOrNull { -it.verticalOffset } ?: 0f
//        lowerHeight = drawingParts.maxOfOrNull { it.verticalOffset + it.height } ?: 0f
//        allOverHeight = lowerHeight+upperHeight
        leftMargin = maxOf(staticMarginLeft, thumbPartsInfo.horizontalCenter, markerPartsInfo.horizontalCenter)
        rightMargin = maxOf(staticMarginRight, thumbPartsInfo.width-thumbPartsInfo.horizontalCenter, markerPartsInfo.width-markerPartsInfo.horizontalCenter)
        horizontalMargin = leftMargin + rightMargin
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
            MeasureSpec.AT_MOST-> min(ceil(allOverOutline().height).roundToInt(), heightSize)
            //MeasureSpec.UNSPECIFIED->allOverOutline().height.roundToInt()
            else -> ceil(allOverOutline().height).roundToInt()
        }
        setMeasuredDimension(width,height)
    }

    private var viewWidth = 0f
    private var viewHeight = 0f
    private var sliderRange = 0f
//    private var sliderTop = 0f
    private var upperMargin = 0f

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = (right - left).toFloat()
        val h = (bottom - top).toFloat()
        if(viewWidth == w && viewHeight == h) {
            return
        }
        viewWidth = w
        viewHeight = h
        sliderRange = viewWidth - horizontalMargin
//        sliderTop = (viewHeight - allOverHeight)/2f
        upperMargin = (viewHeight - allOverOutline().height)/2f
    }

    private val canvasLayer = CanvasLayer()
    override fun onDraw(canvas: Canvas) {
        if(naturalDuration==0L) return

        canvasLayer.reset()
        canvasLayer.use {
            if (needCanvasLayer) {
                val allOver = allOverOutline()
                it.open(canvas,0f, upperMargin, leftMargin*2+sliderRange, allOver.height+upperMargin)
            }
//            canvas.drawRect(0f, 0f, viewWidth, viewHeight, Paint().apply { style= Paint.Style.FILL; color=Color.RED })
//            canvas.drawRect(leftMargin, upperMargin, leftMargin+sliderRange, upperMargin+allOverOutline().height, Paint().apply { style= Paint.Style.FILL; color=Color.GREEN })
            for (p in drawingParts) {
                p.draw(canvas)
            }
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

    private class SavedState : BaseSavedState {
        var position: Long = 0L

        constructor(superState: Parcelable?) : super(superState)

        private constructor(source: Parcel) : super(source) {
            position = source.readLong()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeLong(position)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> =
                object : Parcelable.Creator<SavedState> {
                    override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
                    override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
                }
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val ss = SavedState(super.onSaveInstanceState())
        ss.position = mPosition
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        mPosition = state.position
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