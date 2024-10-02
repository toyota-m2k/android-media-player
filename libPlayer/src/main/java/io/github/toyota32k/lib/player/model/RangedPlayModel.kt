package io.github.toyota32k.lib.player.model

/**
 * 部分再生用モデル
 *
 * @param duration 総再生時間
 * @param spanLength 分割されたパートの長さ
 * @param amountOfMovement next()/previous()による移動量（デフォルト60秒）
 */
@Suppress("MemberVisibilityCanBePrivate")
class RangedPlayModel(
    val duration:Long,
    val spanLength:Long,
    val amountOfMovement:Long=60*1000L) {

    private var start:Long = 0
    private var end:Long = spanLength

    init {
        require(duration>0 && amountOfMovement>0 && spanLength<duration && spanLength>=amountOfMovement)
    }

    val currentRange:Range
        get() = Range(start, end)

    val hasNext:Boolean
        get() = end < duration
    val hasPrevious:Boolean
        get() = start > 0

    fun initializeRangeContainsPosition(cur:Long) {
        start = (((cur-1)/amountOfMovement)*amountOfMovement).coerceAtLeast(0)
        end = start + spanLength
        if(end>duration) {
            end = duration
            start = (duration-spanLength).coerceAtLeast(0)
        }
    }

    fun next():Range? {
        if(hasNext) {
            end = (end + amountOfMovement).coerceAtMost(duration)
            start = (end - spanLength).coerceAtLeast(0)
            return currentRange
        }
        return null
    }

    fun previous():Range? {
        if (hasPrevious) {
            start = (((start - 1)/amountOfMovement)*amountOfMovement).coerceAtLeast(0)      // startは、amountOfMovementの倍数（切り上げ）にする。
            end = (start + spanLength).coerceAtMost(duration)
            return currentRange
        }
        return null
    }
}