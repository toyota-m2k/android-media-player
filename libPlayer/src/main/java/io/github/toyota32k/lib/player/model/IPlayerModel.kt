package io.github.toyota32k.lib.player.model

import android.app.Application
import android.util.Size
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.ui.PlayerView
import io.github.toyota32k.binder.command.IUnitCommand
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.android.RefBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration



interface IPhotoSlideShowModel {
    val isPhotoViewerEnabled: Boolean
    val photoSlideShowDuration: Duration
    val isPhotoSlideShowEnabled: Boolean
    val photoSizeOption: PhotoSizeOption

    fun enablePhotoViewer(flag:Boolean, slideShowDuration: Duration=Duration.INFINITE, photoSizeOption: PhotoSizeOption=PhotoSizeOption.Original)
}

/**
 * PlayerModel の基本 i/f
 */
interface IPlayerModel : AutoCloseable {
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

    /**
     * コマ送りの開始
     * @param forward   true:進む / false:戻る
     * @param stepMs    コマ送りのシーク時間(ms) / 0: 最小単位(frameDuration) / <0: キーフレームへシーク
     */
    fun startFrameStepping(forward:Boolean, stepMs: Long, intervalMs: Long)

    /**
     * コマ送り終了
     */
    fun stopFrameStepping()


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
//    fun enablePhotoViewer(duration: Duration)
    val shownBitmap: StateFlow<RefBitmap?>

    // ExoPlayerHost から呼ぶ
    fun attachPhotoView(photoView: ImageView) : IDisposable

    val currentSourceType: StateFlow<String?> // get() = currentSource.map { it?.type }.stateIn(scope, SharingStarted.Lazily,null)
    val isCurrentSourcePhoto: StateFlow<Boolean> // get
    val isCurrentSourceVideo: StateFlow<Boolean> // get

    val chapterList: StateFlow<IChapterList?>
/*
*/
    /**
     * 再生中のエラーリカバリーハンドラ
     * 再生中にトークンが切れて ExoPlayerが 401 エラーなどを受け取ったときのリカバリー処理を実装する。
     * handler では、例外の内容に従って、IMediaSource の修復を行い、修復出来たらそのインスタンスを返し、
     * 修復不能な場合は、null を返す。
     *
     * 認証トークン切れが発生したときに、再認証してリトライする例：
     *
     * IMediaSource で、IMediaSourcePreparerを継承し、onSourceLoading() で認証処理を実装しておく。
     * 次に、
     *   private suspend fun recoverItemError(e:Throwable, item: IMediaSource): IMediaSource? {
     *       val cause = e.cause ?: return null
     *       if (cause is HttpDataSource.InvalidResponseCodeException) {
     *           if (cause.responseCode == 401) {
     *               return item
     *           }
     *       }
     *       return null
     *   }
     * を、setItemErrorRecoveringHandler(::recoverItemError) でセットする。
     * この例では、受け取った item をそのままreturnしており、setSource() をやり直すことにより、onSourceLoadingが呼ばれて再認証が実行される。
     */
    fun setItemErrorRecoveringHandler(handler:(suspend (Throwable, IMediaSource)->IMediaSource?)?)

    // region photo viewer supports

    val photoViewerOption: StateFlow<PhotoViewerOption>
    fun setPhotoViewerOption(option:PhotoViewerOption)

    // endregion
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

/**
 * プレイリストから、IMediaSource が setSource() される前に prepare() を呼び出すチャンスを与えるi/f
 * 利用する場合は、IMediaSource の実装クラスで、このi/f を継承する。
 * プレーヤーにURLをセットする前に認証を行う、などの用途を想定。
 *
 * さらに、IPlayerModel#setItemErrorRecoveringHandler() と組み合わせることにより、再生中にトークンの有効期限が切れたときの
 * リカバリー処理を実装できる。
 */
interface IMediaSourcePreparer {
    suspend fun onSourceLoading():Boolean
    suspend fun onSourceLoaded()
}