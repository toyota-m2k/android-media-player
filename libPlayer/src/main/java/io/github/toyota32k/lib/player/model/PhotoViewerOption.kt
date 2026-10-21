package io.github.toyota32k.lib.player.model

import kotlin.time.Duration

enum class PhotoSizeOption {
    Original,
    FitToImageView,
    LimitByScreen,
}

data class PhotoViewerOption(
    val isEnabled: Boolean,
    val slideShowDuration: Duration = Duration.INFINITE,
    val sizeOption: PhotoSizeOption = PhotoSizeOption.Original,
) {
    val isSlideShowEnabled: Boolean get() = isEnabled && slideShowDuration != Duration.INFINITE && 0<slideShowDuration.inWholeSeconds
    companion object {
        val DISABLED = PhotoViewerOption(false)
    }
}