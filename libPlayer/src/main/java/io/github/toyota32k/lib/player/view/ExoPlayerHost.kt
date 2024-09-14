package io.github.toyota32k.lib.player.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.FitMode
import io.github.toyota32k.lib.player.common.UtFitter
import io.github.toyota32k.lib.player.R
import io.github.toyota32k.lib.player.common.StyledAttrRetriever
import io.github.toyota32k.lib.player.databinding.V2VideoExoPlayerBinding
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.utils.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlin.math.abs

@Suppress("unused")
class ExoPlayerHost @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = TpLib.logger
    }

    // 使う人（ActivityやFragment）がセットすること
    private lateinit var model: PlayerControllerModel
    val controls: V2VideoExoPlayerBinding

    val playerView get() = controls.expPlayerView
    val rootView get() = controls.expPlayerRoot

    var useExoController:Boolean
        get() = playerView.useController
        set(v) { playerView.useController = v }

    private val rootViewSize = MutableStateFlow<Size?>(null)

    init {
        controls = V2VideoExoPlayerBinding.inflate(LayoutInflater.from(context), this, true)
        StyledAttrRetriever(context,attrs,R.styleable.ExoPlayerHost,defStyleAttr,0).use { sar ->
            controls.expPlayerRoot.background = sar.getDrawable(
                R.styleable.ExoPlayerHost_ampPlayerBackground,
                com.google.android.material.R.attr.colorSurface,
                Color.BLACK
            )
        }
    }

    fun associatePlayer() {
        model.playerModel.associatePlayerView(playerView)
    }
    fun dissociatePlayer() {
        model.playerModel.dissociatePlayerView(playerView)
    }

    fun bindViewModel(playerControllerModel: PlayerControllerModel, binder: Binder) {
        val owner = lifecycleOwner()!!
        val scope = owner.lifecycleScope

        this.model = playerControllerModel
        val playerModel = playerControllerModel.playerModel
        if(playerControllerModel.autoAssociatePlayer) {
            playerModel.associatePlayerView(playerView)
        }

        binder
            .visibilityBinding(controls.expProgressRing, playerModel.isLoading, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible)
            .visibilityBinding(controls.expErrorMessage, playerModel.isError, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible)
            .visibilityBinding(controls.serviceArea, combine(playerModel.isLoading,playerModel.isError) { l, e-> l||e}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible)
            .textBinding(controls.expErrorMessage, playerModel.errorMessage.filterNotNull())
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
                playerView.setLayoutSize(mFitter.resultWidth.toInt(), mFitter.resultHeight.toInt())
                playerView.translationY = 0f
            } else {
                mFitter
                    .setLayoutSize(rootViewSize)
                    .fit(videoSize.height, videoSize.width)
                playerView.setLayoutSize(mFitter.resultHeight.toInt(), mFitter.resultWidth.toInt())
                playerView.translationY = -(mFitter.resultWidth-mFitter.resultHeight)/2f
            }
            playerView.rotation = rotation.toFloat()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
//        if(!this::model.isInitialized) return
        if(w>0 && h>0) {
            logger.debug("width=$w (${context.px2dp(w)}dp), height=$h (${context.px2dp(h)}dp)")
//            model.playerModel.onRootViewSizeChanged(Size(w, h))
            rootViewSize.value = Size(w,h)
        }
    }

}