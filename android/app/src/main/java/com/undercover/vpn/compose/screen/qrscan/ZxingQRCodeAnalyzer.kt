package com.undercover.vpn.compose.screen.qrscan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

class ZxingQRCodeAnalyzer(
    private val onSuccess: ((String) -> Unit),
    private val onFailure: ((Exception) -> Unit),
    private val onCropArea: ((QRCodeCropArea?) -> Unit)? = null,
) : ImageAnalysis.Analyzer {
    private val qrCodeReader = QRCodeReader()
    private var yDataBuffer: ByteArray? = null
    private var rotatedBuffer: ByteArray? = null

    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    )

    var qrsMode: Boolean = false

    override fun analyze(image: ImageProxy) {
        try {
            val rawYData = image.toYUVData()
            val rawWidth = image.width
            val rawHeight = image.height
            val rotationDegrees = image.imageInfo.rotationDegrees

            // 1. Rotate the camera Y plane according to sensor rotation so it's upright
            val (uprightData, uprightWidth, uprightHeight) = rotateYData(rawYData, rawWidth, rawHeight, rotationDegrees)
            val source = PlanarYUVLuminanceSource(uprightData, uprightWidth, uprightHeight, 0, 0, uprightWidth, uprightHeight, false)

            // Fast path: HybridBinarizer on upright image
            tryDecode(BinaryBitmap(HybridBinarizer(source)))?.let {
                onSuccess(it.text)
                return
            }

            // Smart crop on upright image
            val cropArea = QRCodeSmartCrop.findCropArea(uprightData, uprightWidth, uprightHeight, 0)
            onCropArea?.invoke(cropArea)
            if (cropArea != null) {
                val cropWidth = cropArea.right - cropArea.left
                val cropHeight = cropArea.bottom - cropArea.top
                if (cropWidth > 10 && cropHeight > 10) {
                    val smartSource = PlanarYUVLuminanceSource(
                        uprightData,
                        uprightWidth,
                        uprightHeight,
                        cropArea.left,
                        cropArea.top,
                        cropWidth,
                        cropHeight,
                        false,
                    )
                    tryDecode(BinaryBitmap(HybridBinarizer(smartSource)))?.let {
                        onSuccess(it.text)
                        return
                    }
                }
            }

            // In QRS mode, skip extra binarizers for frame rate
            if (qrsMode) return

            // Inverted HybridBinarizer (for dark mode QR codes)
            tryDecode(BinaryBitmap(HybridBinarizer(source.invert())))?.let {
                onSuccess(it.text)
                return
            }

            // GlobalHistogramBinarizer
            tryDecode(BinaryBitmap(GlobalHistogramBinarizer(source)))?.let {
                onSuccess(it.text)
                return
            }

            // GlobalHistogramBinarizer (inverted)
            tryDecode(BinaryBitmap(GlobalHistogramBinarizer(source.invert())))?.let {
                onSuccess(it.text)
                return
            }

            // Fallback: If rotation was non-zero, also try raw unrotated buffer
            if (rotationDegrees != 0) {
                val rawSource = PlanarYUVLuminanceSource(rawYData, rawWidth, rawHeight, 0, 0, rawWidth, rawHeight, false)
                tryDecode(BinaryBitmap(HybridBinarizer(rawSource)))?.let {
                    onSuccess(it.text)
                    return
                }
            }
        } catch (_: NotFoundException) {
            // Normal when no QR is in frame
        } catch (_: ChecksumException) {
        } catch (_: FormatException) {
        } catch (e: Exception) {
            onFailure(e)
        } finally {
            qrCodeReader.reset()
            image.close()
        }
    }

    private data class RotatedResult(val data: ByteArray, val width: Int, val height: Int)

    private fun rotateYData(data: ByteArray, width: Int, height: Int, rotationDegrees: Int): RotatedResult {
        if (rotationDegrees == 0) return RotatedResult(data, width, height)
        val size = width * height
        val out = rotatedBuffer?.takeIf { it.size >= size } ?: ByteArray(size).also { rotatedBuffer = it }

        return when (rotationDegrees) {
            90 -> {
                var k = 0
                for (x in 0 until width) {
                    for (y in height - 1 downTo 0) {
                        out[k++] = data[y * width + x]
                    }
                }
                RotatedResult(out, height, width)
            }
            180 -> {
                for (i in 0 until size) {
                    out[i] = data[size - 1 - i]
                }
                RotatedResult(out, width, height)
            }
            270 -> {
                var k = 0
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        out[k++] = data[y * width + x]
                    }
                }
                RotatedResult(out, height, width)
            }
            else -> RotatedResult(data, width, height)
        }
    }

    private fun ImageProxy.toYUVData(): ByteArray {
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val size = width * height

        val yData = yDataBuffer?.takeIf { it.size >= size } ?: ByteArray(size).also { yDataBuffer = it }
        if (rowStride == width) {
            yBuffer.get(yData, 0, size)
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * rowStride)
                yBuffer.get(yData, row * width, width)
            }
        }
        return yData
    }

    private fun tryDecode(bitmap: BinaryBitmap): Result? = try {
        qrCodeReader.decode(bitmap, hints)
    } catch (_: NotFoundException) {
        qrCodeReader.reset()
        null
    }
}
