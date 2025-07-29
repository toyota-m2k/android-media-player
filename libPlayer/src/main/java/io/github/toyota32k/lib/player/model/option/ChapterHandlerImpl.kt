package io.github.toyota32k.lib.player.model.option

import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.IChapterHandler
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.IPlayerModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class ChapterHandlerImpl(
    val playerModel: IPlayerModel,
    override val hideChapterViewIfEmpty: Boolean) : IChapterHandler {
//    init {
//        playerModel.currentSource.onEach(::onSourceChanged).launchIn(playerModel.scope)
//    }

//    override val chapterList = MutableStateFlow<IChapterList?>(null)
//    override val hasChapters = MutableStateFlow(false)
//    @Suppress("unused")
//    val chapterList:IChapterList? get() = (playerModel.currentSource.value as? IMediaSourceWithChapter)?.chapterList

//    override var disabledRanges:List<Range>? = null
//        private set

    override val commandNextChapter = LiteUnitCommand(::nextChapter)
    override val commandPrevChapter = LiteUnitCommand(::prevChapter)


//    private suspend fun onSourceChanged(src: IMediaSource?) {
//        chapterList.value = null
//        hasChapters.value = false
//        if(src !is IMediaSourceWithChapter) return
//
//        src.prepareChapterList()?.apply {
//            chapterList.value = this
//            hasChapters.value = chapters.isNotEmpty()
//        }
//    }

    private fun seekToChapter(getChapter:(chapterList:IChapterList, current:Long)-> IChapter?) {
        val src = playerModel.currentSource.value as? IMediaSourceWithChapter ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val chapterList = src.getChapterList()
            val chapter = getChapter(chapterList, playerModel.currentPosition) ?: return@launch
            playerModel.seekTo(chapter.position)
        }
    }

    private fun nextChapter() {
        seekToChapter { chapterList, current ->
            chapterList.next(current)
        }
    }

    private fun prevChapter() {
        seekToChapter { chapterList, current ->
            chapterList.prev(current)
        }
    }
}