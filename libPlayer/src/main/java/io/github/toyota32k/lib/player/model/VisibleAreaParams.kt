package io.github.toyota32k.lib.player.model

import org.json.JSONObject

/**
 * ズーム＆スクロール中の Video/Photo の表示エリアの位置情報を表すデータクラス
 *
 * rsx, rsy: 左上の位置 (0.0～1.0)
 * rex, rey: 右下の位置 (0.0～1.0)
 */
data class VisibleAreaParams(
    val rsx:Float,
    val rsy:Float,
    val rex:Float,
    val rey:Float,
) {
    companion object {
        @Suppress("unused")
        fun fromSize(sourceWidth:Int, sourceHeight:Int, sx:Float, sy:Float, w:Float, h:Float):VisibleAreaParams {
            return VisibleAreaParams(
                (sx / sourceWidth.toFloat()).coerceIn(0f, 1f),
                (sy / sourceHeight.toFloat()).coerceIn(0f, 1f),
                ((sx+w) / sourceWidth.toFloat()).coerceIn(0f, 1f),
                ((sy+h) / sourceHeight.toFloat()).coerceIn(0f, 1f),
            )
        }
        val IDENTITY:VisibleAreaParams = VisibleAreaParams(0f, 0f, 1f, 1f)
        fun fromJson(json:String?):VisibleAreaParams {
            if (json==null) return IDENTITY
            val obj = JSONObject(json)
            return VisibleAreaParams(
                obj.getDouble("rsx").toFloat(),
                obj.getDouble("rsy").toFloat(),
                obj.getDouble("rex").toFloat(),
                obj.getDouble("rey").toFloat(),
            )
        }
    }
    val isIdentity:Boolean get() {
        return rsx==0f && rsy==0f && rex==1f && rey==1f
    }
    fun serialize():String {
        return JSONObject().apply {
            put("rsx", rsx)
            put("rsy", rsy)
            put("rex", rex)
            put("rey", rey)
        }.toString()
    }
}
