package io.github.toyota32k.lib.player.model

import androidx.core.net.toUri
import io.github.toyota32k.lib.player.model.BasicPlayerModel.Companion.logger
import io.github.toyota32k.utils.android.IUtFile
import io.github.toyota32k.utils.android.RefBitmap
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

    /**
     * Errorとして扱うかどうか
     */
    val error: Boolean
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
class BitmapInfo private constructor(override val bitmap: RefBitmap?, override val cacheHint: Any?, override val error:Boolean=false) : IBitmapInfo {
    companion object {
        /**
         * 標準のGlideローダーを使用する（cacheHintを使用しない）。
         * IPhotoLoader を PlayerModel にセットしなかった場合のデフォルトの動作。
         */
        val useGlide: IBitmapInfo by lazy { BitmapInfo(null, null) }

        /**
         * エラー用 BitmapInfo（画面上に "ERROR" を表示する
         * エラー表示を行わないばあいは、IBitmapInfo として null を返す。
         */
        val asError: IBitmapInfo by lazy { BitmapInfo(null,null,true) }


        /**
         * カスタムなcacheHintとともに、Glide ローダーを使用する。
         */
        fun useGlideWithCustomHint(cacheHint: Any?): IBitmapInfo = BitmapInfo(null, cacheHint)

        /**
         * fileのsha1ハッシュを cacheHintにして Glide ローダーを使用する。
         */
        fun useGlide(file: File): IBitmapInfo = BitmapInfo(null, sha1OfFile(file))

        /**
         * uriのsha1ハッシュを cacheHintにして Glide ローダーを使用する。
         * file: スキーマの uri に限る。
         */
        fun useGlide(uri: String): IBitmapInfo = BitmapInfo(null, sha1OfFile(uri))

        /**
         * ロードしたビットマップをセット
         */
        fun withBitmap(bitmap: RefBitmap): IBitmapInfo = BitmapInfo(bitmap, null)

        // region HASH utilities

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

        private fun sha1OfFile(utFile: IUtFile): String? {
            return try {
                utFile.fileInputStream { stream->
                    sha1OfStream(stream)
                }
            } catch (e:Throwable) {
                logger.error(e)
                null
            }
        }

        private fun sha1OfFile(uri: String): String? {
            return sha1OfFile(uri.toUri().toUtFile())
        }

        private fun sha1OfFile(file: File): String? {
            return sha1OfFile(file.toUtFile())
        }

        // endregion
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