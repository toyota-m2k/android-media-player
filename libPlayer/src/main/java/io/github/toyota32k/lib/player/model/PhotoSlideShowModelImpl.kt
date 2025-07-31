package io.github.toyota32k.lib.player.model

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

