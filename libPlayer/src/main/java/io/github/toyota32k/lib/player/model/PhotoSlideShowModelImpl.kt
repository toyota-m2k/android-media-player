package io.github.toyota32k.lib.player.model

import kotlin.time.Duration

/**
 * Support PhotoViewer / SlideShow
 */
class PhotoSlideShowModelImpl : IPhotoSlideShowModel {
    override var photoSlideShowDuration: Duration = Duration.INFINITE
    override var isPhotoViewerEnabled: Boolean = false
    override val isPhotoSlideShowEnabled: Boolean
        get() = isPhotoViewerEnabled && photoSlideShowDuration != Duration.INFINITE && 0<photoSlideShowDuration.inWholeSeconds
    override var photoSizeOption: PhotoSizeOption = PhotoSizeOption.Original

    override fun enablePhotoViewer(flag: Boolean, slideShowDuration: Duration, photoSizeOption: PhotoSizeOption) {
        isPhotoViewerEnabled = flag
        photoSlideShowDuration = slideShowDuration
        this.photoSizeOption = photoSizeOption
    }
}

