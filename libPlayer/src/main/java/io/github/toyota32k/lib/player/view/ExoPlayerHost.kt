package io.github.toyota32k.lib.player.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.annotation.OptIn
import androidx.core.graphics.createBitmap
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.combinatorialVisibilityBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.databinding.V2VideoExoPlayerBinding
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.VisibleAreaParams
import io.github.toyota32k.utils.FlowableEvent
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.StyledAttrRetriever
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.getLayoutHeight
import io.github.toyota32k.utils.android.getLayoutWidth
import io.github.toyota32k.utils.android.lifecycleOwner
import io.github.toyota32k.utils.android.px2dp
import io.github.toyota32k.utils.android.setLayoutSize
import io.github.toyota32k.utils.gesture.IUtManipulationTarget
import io.github.toyota32k.utils.gesture.UtAbstractManipulationTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
class ExoPlayerHost @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), PlayerControllerModel.IScreenshotSource {
    companion object {
        val logger get() = TpLib.logger
    }

    // 使う人（ActivityやFragment）がセットすること
    private lateinit var model: PlayerControllerModel
    val controls: V2VideoExoPlayerBinding =
        V2VideoExoPlayerBinding.inflate(LayoutInflater.from(context), this, true)

    private val playerContainer get() = controls.expPlayerContainer
    private val exoPlayer get() = controls.expPlayerView
    private val photoView get() = controls.expPhotoView

    val rootView get() = controls.expPlayerRoot

    enum class ProgressRingSize(val value:Int) {
        Small(1),
        Medium(2),
        Large(3),
        None(4),
        ;
        companion object {
            fun fromValue(value:Int): ProgressRingSize = entries.firstOrNull { it.value==value } ?: Medium
        }
    }
    private var progressRingGravity: Int = 0
    private var progressRingSize:ProgressRingSize = ProgressRingSize.Medium
    private val progressRing:ProgressBar?
        get() = when (progressRingSize) {
            ProgressRingSize.Small -> controls.expProgressRingSmall
            ProgressRingSize.Medium -> controls.expProgressRingMedium
            ProgressRingSize.Large -> controls.expProgressRingLarge
            ProgressRingSize.None -> null
        }

    var useExoController:Boolean
        get() = exoPlayer.useController
        set(v) { exoPlayer.useController = v }

    private val rootViewSize = MutableStateFlow<Size?>(null)

    fun setPlayerAttributes(sar: StyledAttrRetriever) {
        if (sar.sa.getBoolean(R.styleable.ControlPanel_ampAttrsByParent, true)) {
            controls.expPlayerRoot.background = sar.getDrawable(
                R.styleable.ControlPanel_ampPlayerBackground,
                com.google.android.material.R.attr.colorSurface,
                Color.BLACK
            )
        }
        if (sar.sa.getBoolean(R.styleable.ControlPanel_ampPlayerCenteringVertically, false)) {
            val params = controls.expPlayerView.layoutParams as FrameLayout.LayoutParams
            params.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            controls.expPlayerContainer.layoutParams = params
        }

        val ringGravity = sar.sa.getInt(R.styleable.ControlPanel_ampPlayerProgressRingGravity, 0)
        if (ringGravity!=0) {
            progressRingGravity = ringGravity
        }
        val ringSize = sar.sa.getInt(R.styleable.ControlPanel_ampPlayerProgressRingSize, 0)
        if (ringSize!=0) {
            progressRingSize = ProgressRingSize.fromValue(ringSize)
        }
    }

    init {
        StyledAttrRetriever(context,attrs,R.styleable.ControlPanel,defStyleAttr,0).use { sar ->
            setPlayerAttributes(sar)
        }
    }

    fun associatePlayer() {
        model.playerModel.associatePlayerView(exoPlayer)
    }
    fun dissociatePlayer() {
        model.playerModel.dissociatePlayerView(exoPlayer)
    }

    fun bindViewModel(playerControllerModel: PlayerControllerModel, binder: Binder) {
        val owner = lifecycleOwner()!!
        val scope = owner.lifecycleScope

        this.model = playerControllerModel
        val playerModel = playerControllerModel.playerModel
        if(playerControllerModel.autoAssociatePlayer) {
            playerModel.associatePlayerView(exoPlayer)
        }
        playerControllerModel.exoPlayerSnapshotSource = this

        val activeProgressRing = progressRing
        if (progressRingGravity!=0 && activeProgressRing!=null) {
            val params = activeProgressRing.layoutParams as FrameLayout.LayoutParams
            params.gravity = progressRingGravity
            activeProgressRing.layoutParams = params
        }

        binder
            .conditional(activeProgressRing!=null) {
                visibilityBinding(activeProgressRing!!, playerModel.isLoading, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible)
            }
            .visibilityBinding(controls.expErrorMessage, playerModel.isError, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible)
            .visibilityBinding(controls.serviceArea, combine(playerModel.isLoading,playerModel.isError) { l, e-> l||e}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible)
            .textBinding(controls.expErrorMessage, playerModel.errorMessage.filterNotNull())
            .combinatorialVisibilityBinding(model.playerModel.currentSource.map { model.playerModel.isPhotoViewerEnabled && it?.isPhoto == true }) {
                straightInvisible(photoView)
                inverseInvisible(exoPlayer)
            }
            .conditional( model.playerModel.isPhotoViewerEnabled ) {
                model.playerModel.attachPhotoView(photoView)
            }
        combine(playerModel.videoSize, playerModel.rotation, rootViewSize, this::updateLayout).launchIn(scope)
    }

    private val mFitter = UtFitter(FitMode.Inside)
    private fun updateLayout(videoSize:Size?, rotation:Int, rootViewSize:Size?) {
        if(rootViewSize==null||videoSize==null) return
        logger.debug("layoutSize = ${videoSize.width} x ${videoSize.height}")

        handler?.post {
            if(abs(rotation%180) == 0) {
                mFitter
                    .setLayoutSize(rootViewSize)
                    .fit(videoSize)
                playerContainer.setLayoutSize(mFitter.resultWidth.toInt(), mFitter.resultHeight.toInt())
                playerContainer.translationY = 0f
            } else {
                mFitter
                    .setLayoutSize(rootViewSize)
                    .fit(videoSize.height, videoSize.width)
                playerContainer.setLayoutSize(mFitter.resultHeight.toInt(), mFitter.resultWidth.toInt())
                playerContainer.translationY = -(mFitter.resultWidth-mFitter.resultHeight)/2f
            }
            playerContainer.rotation = rotation.toFloat()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if(w>0 && h>0) {
            logger.debug("width=$w (${context.px2dp(w)}dp), height=$h (${context.px2dp(h)}dp)")
            rootViewSize.value = Size(w,h)
        }
    }

    /**
     * VideoPlayerView にズーム機能を付加するための最小限のIUtManipulationTarget実装
     */
    class SimpleManipulationTarget(override val parentView: View, override val contentView: View) : UtAbstractManipulationTarget()

    val manipulationTarget: IUtManipulationTarget
        get() = SimpleManipulationTarget(controls.root, controls.expPlayerContainer) // if(model.playerModel.isPhotoViewerEnabled) ExtendedManipulationTarget() else SimpleManipulationTarget(controls.root, controls.photoView)


    fun takeScreenshotWithPixelCopy(surfaceView: SurfaceView, callback: (Bitmap?) -> Unit) {
        val bitmap: Bitmap = createBitmap(surfaceView.width, surfaceView.height)
        try {
            val handlerThread = HandlerThread("PixelCopier")
            handlerThread.start()
            PixelCopy.request(
                surfaceView, bitmap,
                { copyResult:Int ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        callback(bitmap)
                    }
                    handlerThread.quitSafely()
                },
                Handler(handlerThread.looper)
            )
        } catch (e: IllegalArgumentException) {
            callback(null)
            e.printStackTrace()
        }
    }

    private suspend fun takeScreenshotWithSurfaceView(surfaceView: SurfaceView): Bitmap? {
        logger.debug("capture from SurfaceView")
        return suspendCoroutine { cont ->
            takeScreenshotWithPixelCopy(surfaceView) { bmp->
                cont.resume(bmp)
            }
        }
    }

    private suspend fun takeScreenshotWithTextureView(textureView: TextureView): Bitmap? {
        logger.debug("capture from TextureView")
        return withContext(Dispatchers.IO) {
            textureView.bitmap
        }
    }

    override suspend fun takeScreenshot(): Bitmap? {
        val event = FlowableEvent()
        var listener: OnLayoutChangeListener? = null
        val videoSize = model.playerModel.videoSize.value
        if (videoSize!=null) {
            listener = OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                if (right - left == videoSize.width && bottom - top == videoSize.height) {
                    event.set()
                }
            }
            exoPlayer.addOnLayoutChangeListener(listener)
            val scaleX = exoPlayer.getLayoutWidth() / videoSize.width.toFloat()
            val scaleY = exoPlayer.getLayoutHeight() / videoSize.height.toFloat()
            exoPlayer.setLayoutSize(videoSize.width, videoSize.height)
            exoPlayer.scaleX = scaleX
            exoPlayer.scaleY = scaleY
        }
        event.waitOne(1000L)
        @OptIn(UnstableApi::class)
        val surfaceView = exoPlayer.videoSurfaceView
        if (surfaceView !is SurfaceView && surfaceView !is android.view.TextureView) {
            logger.error("Unknown surface view type: ${surfaceView?.javaClass?.name}")
            return null
        }
        return try {
            if (surfaceView is SurfaceView) {
                takeScreenshotWithSurfaceView(surfaceView)
            } else {
                takeScreenshotWithTextureView(surfaceView as TextureView)
            }
        } finally {
            if (listener != null) {
                exoPlayer.removeOnLayoutChangeListener(listener)
                exoPlayer.setLayoutSize(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                exoPlayer.scaleX = 1f
                exoPlayer.scaleY = 1f
            }
        }
    }

    fun getVisibleAreaParams(): VisibleAreaParams? {
//        val bitmap = viewModel.playlist.photoBitmap.value ?: return null

        val srcSize = model.playerModel.videoSize.value ?: return null
        val sourceWidth: Int = srcSize.width
        val sourceHeight: Int = srcSize.height

        val scale = playerContainer.scaleX               // x,y 方向のscaleは同じ
        if (scale == 1f) return VisibleAreaParams.IDENTITY

        val rtx = playerContainer.translationX
        val rty = playerContainer.translationY

        val tx = rtx / scale
        val ty = rty / scale

        val vw = playerContainer.width                   // imageView のサイズ
        val vh = playerContainer.height
        val fitter = UtFitter(FitMode.Inside, vw, vh)
        fitter.fit(sourceWidth, sourceHeight)
        val iw = fitter.resultWidth                         // imageView内での bitmapの表示サイズ
        val ih = fitter.resultHeight
        val mx = (vw-iw)/2                                  // imageView と bitmap のマージン
        val my = (vh-ih)/2

        // scale: 画面中央をピボットとする拡大率
        // translation：中心座標の移動距離 x scale
        val sw = vw / scale                                 // scaleを補正した表示サイズ
        val sh = vh / scale
        val cx = vw/2f - tx                                 // 現在表示されている画面の中央の座標（scale前の元の座標系）
        val cy = vh/2f - ty
        val sx = max(cx - sw/2 - mx, 0f)              // 表示されている画像の座標（表示画像内の座標系）
        val sy = max(cy - sh/2 - my, 0f)
        val ex = min(cx + sw/2 - mx, iw)
        val ey = min(cy + sh/2 - my, ih)

        val bs = sourceWidth.toFloat()/iw                            // 画像の拡大率を補正して、元画像座標系に変換
        val x = sx * bs
        val y = sy * bs
        val w = (ex - sx) * bs
        val h = (ey - sy) * bs

        return VisibleAreaParams.fromSize(sourceWidth, sourceHeight, x, y, w, h)
    }
    

}