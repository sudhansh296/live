package com.coderlobby.hivo.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Prepares a chosen or captured photo: first an upright, memory-safe copy to crop on screen, then the cropped square
 * as a small JPEG for upload. The server still re-checks, re-crops and re-encodes whatever it receives.
 */
object ImageUtils {
    /** The crop screen works on a picture whose longest side is at most about this many pixels. */
    private const val MAX_DECODE_SIDE = 3072
    private const val UPLOAD_SIDE = 1024
    private const val JPEG_QUALITY = 90

    /** An upright bitmap to crop, or null if the file is not a readable picture. */
    suspend fun loadUpright(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // With inJustDecodeBounds the call returns no bitmap; the size is written into `bounds`.
            val opened = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false
            if (!opened || bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            // Decode at a power-of-two reduction so a 50 MP photo does not fill the phone's memory.
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null

            val degrees = resolver.openInputStream(uri)?.use { rotationDegrees(ExifInterface(it)) } ?: 0
            if (degrees == 0) return@withContext decoded
            val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            if (upright !== decoded) decoded.recycle()
            upright
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Cuts the chosen square out of [source] and returns it as JPEG bytes (at most 1024 x 1024). */
    suspend fun cropToJpeg(source: Bitmap, square: CropMath.Square): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val cut = Bitmap.createBitmap(source, square.x, square.y, square.size, square.size)
            val side = min(square.size, UPLOAD_SIDE)
            val sized = if (side == square.size) cut else Bitmap.createScaledBitmap(cut, side, side, true)
            ByteArrayOutputStream().use { out ->
                sized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun rotationDegrees(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
