package io.github.toyota32k.lib.player.model

import io.github.toyota32k.utils.IDisposable
import java.util.concurrent.atomic.AtomicLong

@Suppress("unused")
interface IMediaSource {
    val id:String
    val name:String
    val uri:String
    val trimming: Range
    val type: String    // 拡張子(.なし）
    val startPosition: AtomicLong
    val isPhoto:Boolean get() = when(type.lowercase()) {
        "jpg","jpeg","png", "gif"->true
        else->false
    }
}

interface IMediaSourceWithChapter : IMediaSource {
    suspend fun getChapterList(): IChapterList
}
