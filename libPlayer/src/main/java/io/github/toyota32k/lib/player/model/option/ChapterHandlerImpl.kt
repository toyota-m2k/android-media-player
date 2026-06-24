package io.github.toyota32k.lib.player.model.option

import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.IChapterHandler
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IPlayerModel
import io.github.toyota32k.logger.UtLog

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

//    private fun seekToChapter(getChapter:(chapterList:IChapterList, current:Long)-> IChapter?) {
//        val chapterList = playerModel.chapterList.value ?: return
//        val chapter = getChapter(chapterList, playerModel.currentPosition) ?: return
//        playerModel.seekTo(chapter.position)
//    }

    private fun nextChapter() {
//        seekToChapter { chapterList, current ->
//            chapterList.next(current)
//        }
        val chapterList = playerModel.chapterList.value ?: return
        val pos = chapterList.next(playerModel.currentPosition)?.position ?: (playerModel.naturalDuration.value - 500L)
        playerModel.seekTo(pos)
    }

    private fun prevChapter() {
        val chapterList = playerModel.chapterList.value ?: return
        val current = playerModel.currentPosition
        val pos = if (playerModel.isPlaying.value) {
            // 再生中は、skip指定されたchapterへはシークしない
            // 理由：skip属性を持ったchapterにシークすると、直後に、スキップされて、もとの位置の戻ってしまい不具合に見えるため。
            chapterList.prevEnabledChapter((current-200).coerceAtLeast(0L))?.position
        } else {
            chapterList.prev(current)?.position
        } ?: 0L
        playerModel.seekTo(pos)
    }
}