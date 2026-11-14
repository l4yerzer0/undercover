package com.undercover.vpn.compose.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QRCodeDecoder {

    suspend fun decodeFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return@withContext null
                decodeFromBitmap(bitmap)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        val reader = QRCodeReader()

        // 1. Try standard HybridBinarizer
        try {
            val binary = BinaryBitmap(HybridBinarizer(source))
            return reader.decode(binary, hints).text
        } catch (_: Exception) {}

        // 2. Try GlobalHistogramBinarizer
        try {
            val binary = BinaryBitmap(GlobalHistogramBinarizer(source))
            return reader.decode(binary, hints).text
        } catch (_: Exception) {}

        // 3. Try inverted with HybridBinarizer (for dark-mode QR codes)
        try {
            val binary = BinaryBitmap(HybridBinarizer(source.invert()))
            return reader.decode(binary, hints).text
        } catch (_: Exception) {}

        // 4. Try inverted with GlobalHistogramBinarizer
        try {
            val binary = BinaryBitmap(GlobalHistogramBinarizer(source.invert()))
            return reader.decode(binary, hints).text
        } catch (_: Exception) {}

        return null
    }
}
