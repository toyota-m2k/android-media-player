package io.github.toyota32k.lib.player.model

import androidx.core.net.toUri
import io.github.toyota32k.lib.player.model.BasicPlayerModel.Companion.logger
import io.github.toyota32k.utils.android.RefBitmap
import io.github.toyota32k.utils.android.UtFile
import io.github.toyota32k.utils.android.toUtFile
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * IPhotoLoader#loadBitmap の戻り値のインターフェース
 */
interface IBitmapInfo {
    /**
     * ロードしたビットマップ
     * このフィールドがnullなら、デフォルトのローダー（Glide）が使用される。
     */
    val bitmap: RefBitmap?

    /**
     * bitmapフィールドがnullの場合に、Glide のキャッシュ制御用のsignature（ハッシュ文字列など）を返す。
     * signatureは指定しない（Glide表儒yンのuriによるキャッシュを利用）場合は null で可。
     * bitmap フィールドに有効な RefBitmap が与えられた場合は、このフィールドは無視されます。
     */
    val cacheHint: Any?
}

/**
 * カスタムローダーのインターフェース
 */
fun interface IPhotoLoader {
    /**
     * カスタム Loader
     * @param src    メディアソース
     * @return      ロードしたビットマップ（IBitmapInfo）
     *              エラー：null （ビットマップは表示されない）
     */
    suspend fun loadBitmap(src: IMediaSource): IBitmapInfo?
}

/**
 * IBitmapInfo の実装クラス
 */
class BitmapInfo constructor(override val bitmap: RefBitmap?, override val cacheHint: Any?) : IBitmapInfo {
    companion object {
        /**
         * 標準のGlideローダーを使用する（cacheHintを使用しない）。
         * IPhotoLoader を PlayerModel にセットしなかった場合のデフォルトの動作。
         */
        val useGlide = BitmapInfo(null, null)

        /**
         * カスタムなcacheHintとともに、Glide ローダーを使用する。
         */
        fun useGlide(cacheHint: Any?) = BitmapInfo(null, cacheHint)

        /**
         * fileのsha1ハッシュを cacheHintにして Glide ローダーを使用する。
         */
        fun useGlide(file: File) = BitmapInfo(null, sha1OfFile(file))

        /**
         * uriのsha1ハッシュを cacheHintにして Glide ローダーを使用する。
         * file: スキーマの uri に限る。
         */
        fun useGlide(uri: String) = BitmapInfo(null, sha1OfFile(uri))
        fun withBitmap(bitmap: RefBitmap) = BitmapInfo(bitmap, null)

        private fun sha1OfStream(stream: FileInputStream): String? {
            return try {
                val buffer = ByteArray(1024 * 8)
                val digest = MessageDigest.getInstance("SHA-1")
                var read = stream.read(buffer)
                while (read > 0) {
                    digest.update(buffer, 0, read)
                    read = stream.read(buffer)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            } catch (e:Throwable) {
                logger.error(e)
                null
            }
        }

        private fun sha1OfFile(uri: String): String? {
            return try {
                uri.toUri().toUtFile().fileInputStream { stream->
                    sha1OfStream(stream)
                }
            } catch (e:Throwable) {
                logger.error(e)
                null
            }
        }

    }
}

/**
 * 標準的なPhotoLoader 実装
 */
open class StandardPhotoLoader(private val loader: (suspend (IMediaSource) -> RefBitmap?)?=null) : IPhotoLoader {
    override suspend fun loadBitmap(src: IMediaSource): IBitmapInfo? {
        if (loader==null) {
            return BitmapInfo.useGlide(src.uri)
        } else {
            val bitmap = loader.invoke(src) ?: return BitmapInfo.useGlide(src.uri)
            return BitmapInfo.withBitmap(bitmap)
        }
    }
}