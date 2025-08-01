package io.github.toyota32k.lib.player.model.option

import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.IMediaFeed
import io.github.toyota32k.lib.player.model.IPlayerModel
import io.github.toyota32k.lib.player.model.IPlaylistHandler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PlaylistHandlerImpl(
    val playerModel: IPlayerModel,
    val playlist: IMediaFeed,
    ) : IPlaylistHandler {
    init {
        playlist.currentSource.onEach(::onCurrentSourceChanged).launchIn(playerModel.scope)
    }

    private suspend fun onCurrentSourceChanged(src: IMediaSource?) {
        playerModel.setSource(src)
    }

//    val currentVideo: IAmvSource?
//        get() = currentSource.value

//    override fun onEnd() {
//        playlist.onEnd()
//    }

    override val commandNext = LiteUnitCommand(this::next)
    override val commandPrev = LiteUnitCommand(this::previous)

    private fun next() {
        playlist.next()
    }

    private fun previous() {
        playlist.previous()
    }

    override val hasPrevious: StateFlow<Boolean>
        get() = playlist.hasPrevious

    override val hasNext: StateFlow<Boolean>
        get() = playlist.hasNext
}