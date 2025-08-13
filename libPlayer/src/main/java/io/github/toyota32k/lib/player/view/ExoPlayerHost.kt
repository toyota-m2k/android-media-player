package io.github.toyota32k.lib.player.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.combinatorialVisibilityBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.setLayoutSize
import io.github.toyota32k.lib.player.databinding.V2VideoExoPlayerBinding
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.StyledAttrRetriever
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.lifecycleOwner
import io.github.toyota32k.utils.android.px2dp
import io.github.toyota32k.utils.gesture.IUtManipulationTarget
import io.github.toyota32k.utils.gesture.UtAbstractManipulationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.sql.DataSource
import kotlin.math.abs

@Suppress("unused")
class ExoPlayerHost @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
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
}