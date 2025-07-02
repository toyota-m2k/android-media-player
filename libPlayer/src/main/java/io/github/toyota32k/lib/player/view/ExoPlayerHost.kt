package io.github.toyota32k.lib.player.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
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

    var useExoController:Boolean
        get() = exoPlayer.useController
        set(v) { exoPlayer.useController = v }

    private val rootViewSize = MutableStateFlow<Size?>(null)

    fun setPlayerAttributes(sar: StyledAttrRetriever) {
        if (!sar.sa.getBoolean(R.styleable.ControlPanel_ampAttrsByParent, false)) {
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

        val progressRingGravity = sar.sa.getInt(R.styleable.ControlPanel_ampPlayerProgressRingGravity, 0)
        if (progressRingGravity!=0) {
            val params = controls.expProgressRing.layoutParams as FrameLayout.LayoutParams
            params.gravity = progressRingGravity
            controls.expProgressRing.layoutParams = params
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

        binder
            .visibilityBinding(controls.expProgressRing, playerModel.isLoading, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible)
            .visibilityBinding(controls.expErrorMessage, playerModel.isError, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible)
            .visibilityBinding(controls.serviceArea, combine(playerModel.isLoading,playerModel.isError) { l, e-> l||e}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible)
            .textBinding(controls.expErrorMessage, playerModel.errorMessage.filterNotNull())
            .combinatorialVisibilityBinding(model.playerModel.currentSource.map { model.playerModel.isPhotoViewerEnabled && it?.isPhoto == true }) {
                straightInvisible(photoView)
                inverseInvisible(exoPlayer)
            }
            .conditional( model.playerModel.isPhotoViewerEnabled ) {
                observe(model.playerModel.currentSource) {
                    if(it?.isPhoto == true) {
                        CoroutineScope(Dispatchers.Main).launch {
                            val bitmap = model.playerModel.resolvePhoto(it)
                            photoView.setImageBitmap(bitmap)
                        }
                    } else {
                        photoView.setImageBitmap(null)
                        model.playerModel.resetPhoto()
                    }
                }
            }

//            .bindCommand(playerControllerModel.commandPlayerTapped, this)

//        val matchParent = Size(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
//        combine(playerModel.playerSize, playerModel.stretchVideoToView) { playerSize, stretch ->
//            logger.debug("AmvExoVideoPlayer:Size=(${playerSize.width}w, ${playerSize.height}h (stretch=$stretch))")
//            if(stretch) {
//                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
//                matchParent
//            } else {
//                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
//                playerSize
//            }
//        }.onEach(this::updateLayout).launchIn(scope)

//        playerModel.rotation.onEach {
//            playerView.rotation = it.toFloat()
//        }.launchIn(scope)

        combine(playerModel.videoSize, playerModel.rotation, rootViewSize, this::updateLayout).launchIn(scope)
    }

    private val mFitter = UtFitter(FitMode.Inside)
    private fun updateLayout(videoSize:Size?, rotation:Int, rootViewSize:Size?) {
        if(rootViewSize==null||videoSize==null) return
        logger.debug("layoutSize = ${videoSize.width} x ${videoSize.height}")

        handler?.post {
//            playerView.rotation = rotation.toFloat()
            if(abs(rotation%180) == 0) {
                mFitter
                    .setLayoutSize(rootViewSize)
                    .fit(videoSize)
//                playerView.setLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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