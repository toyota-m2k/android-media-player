package io.github.toyota32k.lib.player.model

import kotlin.math.ceil

/**
 * 部分再生用モデル
 * 分割されたパートの長さ spanLength と、総再生時間 duration を持ち、
 * next/previous によって、再生範囲を前後することができる。
 */
class RangedPlayModel(val duration:Long, val spanLength:Long) {
    companion object {
        const val MIN_SPAN_LENGTH = 60 * 1000   // 1 minute
    }

//    private var currentIndex:Int = 0
    private val overlap = if(spanLength>MIN_SPAN_LENGTH*3) MIN_SPAN_LENGTH else 0
    private var start:Long = 0
    private var end:Long = spanLength

    init {
        require(duration>0 && spanLength<duration && spanLength>=MIN_SPAN_LENGTH)
    }

    private val countOfParts:Int = ceil(duration.toFloat() / spanLength).toInt()

    val currentRange:Range
        get() = Range(start, end)

    val hasNext:Boolean
        get() = end < duration
    val hasPrevious:Boolean
        get() = start > 0

    fun next():Range? {
        if(hasNext) {
            end = (end + spanLength - overlap).coerceAtMost(duration)
            start = end - spanLength
            return currentRange
        }
        return null
    }

    fun previous():Range? {
        if (hasPrevious) {
            val d = spanLength - overlap
            start = (((start - d) + d -1)/d)*d      // startは、(spanLength-overlap)の倍数（切り上げ）にする。
            start = start.coerceAtLeast(0)
            end = (start + spanLength).coerceAtMost(duration)
            return currentRange
        }
        return null
    }
}