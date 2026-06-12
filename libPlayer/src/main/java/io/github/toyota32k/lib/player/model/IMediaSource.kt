package io.github.toyota32k.lib.player.model

import android.media.MediaMetadataRetriever
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

interface IMediaMetadataRetrieverSource {
    suspend fun <T> withMediaMetadataRetriever(fn:suspend (MediaMetadataRetriever)->T) : T
}

interface IMediaSourceWithChapter : IMediaSource {
    suspend fun getChapterList(): IChapterList
}
