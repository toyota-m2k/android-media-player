package io.github.toyota32k.lib.player.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import androidx.core.net.toUri
import io.github.toyota32k.lib.player.model.BasicPlayerModel.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Support PhotoViewer / SlideShow
 */
class PhotoSlideShowModelImpl(context: Context) : IPhotoSlideShowModel {
    private suspend fun defaultResolver(item: IMediaSource): Bitmap? {
        return try {
            withContext(Dispatchers.IO) {
                if (item.uri.startsWith("http")) {
                    (URL(item.uri).openConnection() as HttpURLConnection).run {
                        connect()
                        BitmapFactory.decodeStream(inputStream)
                    }
                } else {
                    context.contentResolver.openInputStream(item.uri.toUri())?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                }
            }
        } catch(e:Throwable) {
            BasicPlayerModel.logger.error(e)
            null
        }
    }
    private val context = context.applicationContext
    override var photoSlideShowDuration: Duration = 5.seconds
        set(v) {
            if(v<=0.seconds || v==Duration.INFINITE) throw IllegalArgumentException("duration must be positive and finite.")
            field = v
        }
    override var photoResolver: (suspend (item: IMediaSource) -> Bitmap?)? = null
    override var resolvedBitmap:Bitmap? = null
    override var isPhotoViewerEnabled: Boolean = false

    fun enablePhotoViewer(duration: Duration, resolver: (suspend (item: IMediaSource) -> Bitmap?)?) {
        if (duration <= 0.seconds || duration==Duration.INFINITE) throw IllegalArgumentException("duration must be positive and finite.")
        isPhotoViewerEnabled = true
        photoSlideShowDuration = duration
        photoResolver = resolver ?: ::defaultResolver
    }

    suspend fun resolvePhoto(item: IMediaSource, state:MutableStateFlow<PlayerState>, videoSize:MutableStateFlow<Size?>): Bitmap? {
        resolvedBitmap = null
        if(!item.isPhoto) return null
        state.value = PlayerState.Loading
        return photoResolver?.let { resolver->
            val bitmap = resolver(item)
            if(bitmap!=null) {
                resolvedBitmap = bitmap
                state.value = PlayerState.Ready
                videoSize.value = Size(bitmap.width, bitmap.height)
            }
            bitmap
        }
    }
    override suspend fun resolvePhoto(item: IMediaSource): Bitmap? {
        error("do not call this method.")
    }
    override fun resetPhoto() {
        resolvedBitmap?.recycle()
        resolvedBitmap = null
    }
}

