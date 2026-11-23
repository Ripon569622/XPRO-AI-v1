package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapUtils {
    /**
     * Decodes a gallery/photo-picker URI into a software-backed Bitmap scaled down so
     * inline multimodal uploads to Firebase AI Logic stay fast, safe from OOM, and
     * compatible with Bitmap.compress() (never HARDWARE config).
     */
    fun decodeSoftwareBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1536
    ): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val width = info.size.width
                    val height = info.size.height
                    val largest = max(width, height)
                    if (largest > maxDimension && width > 0 && height > 0) {
                        val scale = maxDimension.toFloat() / largest.toFloat()
                        decoder.setTargetSize(
                            (width * scale).roundToInt().coerceAtLeast(1),
                            (height * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }

        return decodeWithBitmapFactory(context, uri, maxDimension)
    }

    private fun decodeWithBitmapFactory(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        var sampleSize = 1
        val largest = max(boundsOptions.outWidth, boundsOptions.outHeight)
        while (largest / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Unable to decode selected image.")

        val postLargest = max(decoded.width, decoded.height)
        return if (postLargest > maxDimension && decoded.width > 0 && decoded.height > 0) {
            val scale = maxDimension.toFloat() / postLargest.toFloat()
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
    }
}
