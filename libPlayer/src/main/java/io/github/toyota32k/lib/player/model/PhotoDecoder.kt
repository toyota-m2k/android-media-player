package io.github.toyota32k.lib.player.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import io.github.toyota32k.lib.player.model.BasicPlayerModel.Companion.logger
import io.github.toyota32k.utils.android.RefBitmap
import io.github.toyota32k.utils.android.RefBitmap.Companion.toRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer

object PhotoDecoder {
    suspend fun decodeBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE  // ハードウェア Bitmap は scale/crop の後処理に向かないため
                }
            } else {
                decodeBitmapLegacy(context, uri)
            }
        } catch (e: Throwable) {
            logger.error(e)
            null
        }
    }

    /**
     * メモリ上の画像データ（OkHttp などで取得したもの）をデコードする。
     * bytes はデコード完了まで変更しないこと。
     */
    suspend fun decodeBitmap(bytes: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                decodeBitmapLegacy(bytes)
            }
        } catch (e: Throwable) {
            logger.error(e)
            null
        }
    }

    /**
     * InputStream から読み込んでデコードする。stream は全体をメモリに読み込んだ後に close される。
     */
    suspend fun decodeBitmap(stream: InputStream): Bitmap? {
        val bytes = try {
            withContext(Dispatchers.IO) { stream.use { it.readBytes() } }
        } catch (e: Throwable) {
            logger.error(e)
            return null
        }
        return decodeBitmap(bytes)
    }

    // API 26-27: BitmapFactory でデコードし、EXIF の向きを自前で適用する
    private fun decodeBitmapLegacy(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return applyOrientation(bitmap, orientation)
    }

    private fun decodeBitmapLegacy(bytes: ByteArray): Bitmap? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return applyOrientation(bitmap, orientation)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f); matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f); matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }
}

