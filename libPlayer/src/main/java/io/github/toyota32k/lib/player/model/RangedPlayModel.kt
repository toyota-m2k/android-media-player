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

    private var currentIndex:Int = 0
    private val overlap = spanLength / 8

    init {
        require(duration>0 && spanLength<duration && spanLength>=MIN_SPAN_LENGTH)
    }

    private val countOfParts:Int = ceil(duration.toFloat() / spanLength).toInt()

    val currentRange:Range
        get() = when (currentIndex) {
            0 -> Range(0,spanLength)
            countOfParts-1 -> Range(duration-spanLength,duration)
            else -> {
                val start = (currentIndex * spanLength - overlap).coerceAtLeast(0)
                val end = (start + spanLength + overlap).coerceAtMost(duration)
                Range(start,end)
            }
        }

    val hasNext:Boolean
        get() = currentIndex < countOfParts - 1
    val hasPrevious:Boolean
        get() = currentIndex > 0

    fun next():Range? {
        if(hasNext) {
            currentIndex++
            return currentRange
        }
        return null
    }

    fun previous():Range? {
        if (hasPrevious) {
            currentIndex--
            return currentRange
        }
        return null
    }
}