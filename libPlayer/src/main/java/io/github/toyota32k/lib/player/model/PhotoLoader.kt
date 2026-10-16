package io.github.toyota32k.lib.player.model

import androidx.core.net.toUri
import io.github.toyota32k.lib.player.model.BasicPlayerModel.Companion.logger
import io.github.toyota32k.utils.UtLib
import io.github.toyota32k.utils.android.RefBitmap.Companion.toRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

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

    companion object {
        val defaultLoader:IPhotoLoader by lazy { StandardPhotoLoader() }
    }
}

///**
// * 標準的なPhotoLoader 実装
// */
//open class StandardPhotoLoader(protected val loader: (suspend (IMediaSource) -> RefBitmap?)?=null) : IPhotoLoader {
//    override suspend fun loadBitmap(src: IMediaSource): IBitmapInfo? {
//        if (loader==null) {
//            return BitmapInfo.useGlide(src.uri)
//        } else {
//            val bitmap = loader.invoke(src) ?: return BitmapInfo.useGlide(src.uri)
//            return BitmapInfo.withBitmap(bitmap)
//        }
//    }
//}

interface IHttpStreamProvider {
    suspend fun <T> withStream(uri: String, fn: suspend (InputStream) -> T?):T?
    fun cancel()
}

/**
 * HttpURLConnection を使うシンプルな Stream Provider
 */
object DefaultHttpStreamProvider: IHttpStreamProvider {
    var connection: HttpURLConnection? = null
    override suspend fun <T> withStream(uri: String, fn: suspend (InputStream) -> T?):T? = withContext(Dispatchers.IO) {
        try {
            cancel()
            connection = URL(uri).openConnection() as HttpURLConnection
            connection?.run {
                connect()
                getInputStream().use { stream->
                    fn(stream)
                }
            }
        } catch(e:Throwable) {
            logger.error(e)
            null
        }
        finally {
            cancel()
        }
    }

    override fun cancel() {
        connection?.disconnect()
        connection = null
    }
}

/**
 * 標準的なPhotoLoader 実装
 * gifのみGlideに展開させ、それ以外は自力で展開する。
 * この動作を変更する場合は、IPhotoLoader を独自実装してもよいが、decoder関数（プロパティ）でカスタマイズ可能。
 */
open class StandardPhotoLoader(val httpStreamProvider: IHttpStreamProvider?=null, val decoder: (suspend (IMediaSource) -> IBitmapInfo?)?=null) : IPhotoLoader {
    override suspend fun loadBitmap(src: IMediaSource): IBitmapInfo? {
        if (decoder!=null) {
            // カスタムローダーが定義されていれば試行する。
            val bitmapInfo = decoder.invoke(src)
            if (bitmapInfo!=null) {
                // bitmapInfo が null でなければ、エラーでも Glideには流さない
                return bitmapInfo
            }
            // カスタムローダーが nullを返せば、loader==null だったものとして、標準のフローに fallback
        }
        if (src.type=="gif") {
            // GIFはGlideで処理
            return BitmapInfo.useGlide(src.uri)
        }
        if (src.uri.startsWith("http")) {
            // http filie
            val provider = httpStreamProvider ?: DefaultHttpStreamProvider
            val bitmap = provider.withStream(src.uri) { stream->
                PhotoDecoder.decodeBitmap(stream)
            } ?: return BitmapInfo.asError
            return BitmapInfo.withBitmap(bitmap.toRef())
        }
        // ローカルファイル
        val bitmap = PhotoDecoder.decodeBitmap(UtLib.applicationContext, src.uri.toUri()) ?: return BitmapInfo.asError
        return BitmapInfo.withBitmap(bitmap.toRef())
    }
}