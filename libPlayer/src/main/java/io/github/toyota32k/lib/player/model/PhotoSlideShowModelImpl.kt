package io.github.toyota32k.lib.player.model

import android.graphics.drawable.Drawable
import android.util.Size
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import io.github.toyota32k.binder.observe
import io.github.toyota32k.lib.player.view.ExoPlayerHost.Companion.logger
import io.github.toyota32k.utils.IDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Support PhotoViewer / SlideShow
 */
class PhotoSlideShowModelImpl : IPhotoSlideShowModel {
    override var photoSlideShowDuration: Duration = 5.seconds
        set(v) {
            if(v<=0.seconds || v==Duration.INFINITE) throw IllegalArgumentException("duration must be positive and finite.")
            field = v
        }
    override var isPhotoViewerEnabled: Boolean = false

    fun enablePhotoViewer(duration: Duration) {
        if (duration <= 0.seconds || duration==Duration.INFINITE) throw IllegalArgumentException("duration must be positive and finite.")
        isPhotoViewerEnabled = true
        photoSlideShowDuration = duration
    }

}

