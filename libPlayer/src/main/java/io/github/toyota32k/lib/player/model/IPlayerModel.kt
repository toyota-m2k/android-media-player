package io.github.toyota32k.lib.player.model

import android.app.Application
import android.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.ui.PlayerView
import io.github.toyota32k.binder.command.IUnitCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

interface IPhotoSlideShowModel {
    val isPhotoViewerEnabled: Boolean
    var photoSlideShowDuration: Duration
}

interface IPlayerModel : AutoCloseable, IPhotoSlideShowModel {
    fun setSource(src:IMediaSource?)
    fun setPlayRange(range:Range?)

    fun play()
    fun pause()
    fun stop()
    fun togglePlay()

    fun reset()
    fun seekRelative(seek: Long)
    fun seekRelativeByFrame(frameCount:Long)
    fun seekTo(seek:Long)

    fun associatePlayerView(playerView: PlayerView)
    fun dissociatePlayerView(playerView: PlayerView)

    @OptIn(UnstableApi::class)
    fun associateNotificationManager(manager: PlayerNotificationManager)
    fun onPlaybackCompleted()


    val currentSource: StateFlow<IMediaSource?>
    val videoSize: StateFlow<Size?>

    val rotation: StateFlow<Int>
    fun rotate(value:Rotation)


    val playerSeekPosition: StateFlow<Long>
    val naturalDuration: StateFlow<Long>
    val playRange: StateFlow<Range?>
    val isReady: StateFlow<Boolean>
    val isLoading: StateFlow<Boolean>
    val isPlaying: StateFlow<Boolean>
    val isError: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>
    val volume: MutableStateFlow<Float>  // 0-100
    val mute: MutableStateFlow<Boolean>

    val seekManager:ISeekManager
    val currentPosition:Long
    val continuousPlay:Boolean

    val scope: CoroutineScope
    val context: Application

    fun killPlayer()
    fun revivePlayer():Boolean

    // PhotoViewer /Slide Show を有効化する
    fun enablePhotoViewer(duration: Duration)
}

interface IPlaylistHandler {
    val commandNext: IUnitCommand
    val commandPrev: IUnitCommand

    val hasPrevious:StateFlow<Boolean>
    val hasNext:StateFlow<Boolean>
}

interface  IChapterHandler {
    val hideChapterViewIfEmpty:Boolean
    val commandNextChapter: IUnitCommand
    val commandPrevChapter: IUnitCommand
}