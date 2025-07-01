package io.github.toyota32k.lib.player.model

import android.content.Context
import io.github.toyota32k.lib.player.model.option.ChapterHandlerImpl
import io.github.toyota32k.lib.player.model.option.PlaylistHandlerImpl
import kotlinx.coroutines.CoroutineScope

open class PlaylistPlayerModel (private val playerModel: IPlayerModel, private val playlistHandler: IPlaylistHandler)
    : IPlayerModel by playerModel, IPlaylistHandler by playlistHandler {

    private fun playbackCompleted():Boolean {
        return if(continuousPlay) {
            pause()
            playlistHandler.commandNext.invoke()
            true
        } else {
            false
        }
    }
    init {
        if(playerModel is BasicPlayerModel) {
            playerModel.onPlaybackCompletedHandler = ::playbackCompleted
        }
    }
}

fun PlaylistPlayerModel(context:Context, coroutineScope: CoroutineScope, playlist:IMediaFeed, initialAutoPlay:Boolean, continuousPlay:Boolean):PlaylistPlayerModel {
    val playerModel = BasicPlayerModel(context, coroutineScope, initialAutoPlay, continuousPlay)
    val playlistHandler = PlaylistHandlerImpl(playerModel, playlist)
    return PlaylistPlayerModel(playerModel, playlistHandler)
}

class ChapterPlayerModel (private val playerModel: IPlayerModel, private val chapterHandler: ChapterHandlerImpl)
    : IPlayerModel by playerModel, IChapterHandler by chapterHandler

fun ChapterPlayerModel(context:Context, coroutineScope: CoroutineScope, hideChapterViewIfEmpty: Boolean, initialAutoPlay: Boolean):ChapterPlayerModel {
    val playerModel = BasicPlayerModel(context, coroutineScope, initialAutoPlay, continuousPlay=false)
    val chapterHandler = ChapterHandlerImpl(playerModel,hideChapterViewIfEmpty)
    return ChapterPlayerModel(playerModel, chapterHandler)
}

class PlaylistChapterPlayerModel (playerModel: IPlayerModel, playlistHandler: IPlaylistHandler, private val chapterHandler: ChapterHandlerImpl)
    : PlaylistPlayerModel(playerModel, playlistHandler), IChapterHandler by chapterHandler

fun PlaylistChapterPlayerModel(context: Context, coroutineScope: CoroutineScope, playlist:IMediaFeed, initialAutoPlay: Boolean, continuousPlay: Boolean, hideChapterViewIfEmpty:Boolean):PlaylistChapterPlayerModel {
    val playerModel = BasicPlayerModel(context, coroutineScope,initialAutoPlay, continuousPlay)
    val playlistHandler = PlaylistHandlerImpl(playerModel, playlist)
    val chapterHandler = ChapterHandlerImpl(playerModel,hideChapterViewIfEmpty)
    return PlaylistChapterPlayerModel(playerModel, playlistHandler, chapterHandler)
}

