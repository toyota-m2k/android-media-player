@file:Suppress("unused")

package io.github.toyota32k.lib.player.model

import io.github.toyota32k.utils.lifecycle.Listeners


interface IChapter {
    val position:Long
    val label:String
    val skip:Boolean
}

data class NeighborChapter(val prev:Int, val hit:Int, val next:Int) {
    companion object {
        val empty = NeighborChapter(-1,-1,-1)
    }
    private fun getChapterAt(index:Int, chapters:List<IChapter>):IChapter? {
        if(index<0||chapters.size<=index) return null
        return chapters[index]
    }
    fun getPrevChapter(chapterList:IChapterList):IChapter? {
        return getChapterAt(prev, chapterList.chapters)
    }
    fun getHitChapter(chapterList:IChapterList) :IChapter? {
        return getChapterAt(hit, chapterList.chapters)
    }
    fun getNextChapter(chapterList:IChapterList):IChapter? {
        return getChapterAt(next, chapterList.chapters)
    }
}

interface IChapterList {
    val chapters:List<IChapter>
    fun prev(current:Long) : IChapter?
    fun next(current:Long) : IChapter?
    fun getChapterAround(position:Long):IChapter?
    fun enabledRanges(trimming: Range=Range.empty) : List<Range>
    fun disabledRanges(trimming: Range=Range.empty) : List<Range>

    fun getNeighborChapters(pivot:Long): NeighborChapter

    fun indexOf(position:Long):Int
    val isEmpty:Boolean
        get() = chapters.isEmpty() || (chapters.size==1 && chapters[0].position==0L && !chapters[0].skip)

    val isNotEmpty:Boolean get() = !isEmpty

    @Deprecated("buggy!")
    fun defrag(trimming: Range=Range.empty):List<IChapter>

    fun adjustWithEnabledRanges(enabledRanges:List<Range>):List<IChapter>

    object Empty : IChapterList {
        override val chapters: List<IChapter>
            get() = emptyList()

        override fun prev(current: Long): IChapter? {
            return null
        }

        override fun next(current: Long): IChapter? {
            return null
        }


        override fun getChapterAround(position: Long): IChapter? {
            return null
        }

        override fun enabledRanges(trimming: Range): List<Range> {
            return emptyList()
        }

        override fun disabledRanges(trimming: Range): List<Range> {
            return emptyList()
        }

        override fun getNeighborChapters(pivot: Long): NeighborChapter {
            return NeighborChapter.empty
        }

        override fun indexOf(position: Long): Int {
            return -1
        }

        @Deprecated("buggy!", ReplaceWith("adjustWithEnabledRanges()"))
        override fun defrag(trimming: Range): List<IChapter> {
            return emptyList()
        }

        override fun adjustWithEnabledRanges(enabledRanges:List<Range>):List<IChapter> {
            return emptyList()
        }

    }
}


fun IChapterList.indexOf(chapter: IChapter):Int
        = indexOf(chapter.position)
fun IChapterList.chapterAt(index:Int): IChapter? {
    if(index<0 || chapters.size<=index) return null
    return chapters[index]
}
fun IChapterList.chapterOn(position:Long):IChapter? {
    val i = indexOf(position)
    if(i<0) return null
    return chapterAt(i)
}

interface IMutableChapterList : IChapterList {
    fun initChapters(chapters:List<IChapter>)

    /**
     * チャプターを挿入
     *
     * @param position  挿入位置
     * @param label チャプター名（任意）
     * @param skip false: スキップしない / true:スキップする / null: 挿入位置の状態を継承
     * @return  true: 挿入した / 挿入できなかった
     */
    fun addChapter(position: Long, label:String="", skip:Boolean?=null):Boolean

    /**
     * チャプターの属性を変更
     *
     * @param position  挿入位置
     * @param label チャプター名 / nullなら変更しない
     * @param skip false: スキップしない / true:スキップする / null: 変更しない
     * @return true: 変更した / false: 変更しなかった（チャプターが存在しない、or 属性が変化しない）
     */
    fun updateChapter(position: Long, label:String?=null, skip:Boolean?=null):Boolean

    /**
     * チャプターを削除する
     * @param index 削除するチャプターのindex
     * @return true: 変更した / false: 変更しなかった（チャプターが存在しない　or 削除禁止の先頭チャプター）
     */
    fun removeChapterAt(index:Int):Boolean

    val modifiedListener: Listeners<Unit>
}

fun IMutableChapterList.addChapter(chapter:IChapter):Boolean
        = addChapter(chapter.position, chapter.label, chapter.skip)
fun IMutableChapterList.updateChapter(chapter:IChapter, label:String?=null, skip:Boolean?=null):Boolean
        = updateChapter(chapter.position, label, skip)
fun IMutableChapterList.updateChapter(chapter:IChapter):Boolean
        = updateChapter(chapter.position, chapter.label, chapter.skip)

fun IMutableChapterList.skipChapter(position:Long, skip: Boolean):Boolean {
    return updateChapter(position, null, skip)
}
fun IMutableChapterList.skipChapter(chapter: IChapter, skip: Boolean):Boolean
        = skipChapter(chapter.position, skip)

fun IMutableChapterList.removeChapter(position:Long):Boolean {
    val index = indexOf(position)
    if(index<=0) return false       // index 0 は削除禁止
    return removeChapterAt(index)
}

