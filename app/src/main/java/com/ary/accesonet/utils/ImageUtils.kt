package com.ary.accesonet.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object ImageUtils {

    fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {

        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val newWidth: Int
        val newHeight: Int

        if (width >= height) {
            if (height > maxSize) {
                newWidth = (width * (maxSize.toFloat() / height)).toInt()
                newHeight = maxSize
            } else {
                newWidth = width
                newHeight = height
            }
        } else {
            if (width > maxSize) {
                newWidth = maxSize
                newHeight = (height * (maxSize.toFloat() / width)).toInt()
            } else {
                newWidth = width
                newHeight = height
            }
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val width = 500
        val height = 500
        val inputSize = width * height * 3 * 4
        val byteBuffer = ByteBuffer.allocateDirect(inputSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        resizedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixelValue in pixels) {
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            val normalizedRed = (r / 127.5f) - 1
            val normalizedGreen = (g / 127.5f) - 1
            val normalizedBlue = (b / 127.5f) - 1

            byteBuffer.putFloat(normalizedRed)
            byteBuffer.putFloat(normalizedGreen)
            byteBuffer.putFloat(normalizedBlue)
        }

        return byteBuffer
    }

    fun loadModel(context: Context): Interpreter {
        val assetManager = context.assets
        val modelFileDescriptor = assetManager.openFd("ary.tflite")
        val fileInputStream = FileInputStream(modelFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val mappedByteBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            modelFileDescriptor.startOffset,
            modelFileDescriptor.declaredLength
        )
        return Interpreter(mappedByteBuffer)
    }

    fun classifyNet(bitmap: Bitmap, context: Context): Pair<String, Float> {
        val interpreter = loadModel(context)
        val input = preprocessImage(bitmap)
        val output = Array(1) { FloatArray(13) }

        interpreter.run(input, output)

        val probabilities = output[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

        val fruitName = when (maxIndex) {
            0 -> "Anillos"
            1 -> "Aretes"
            2 -> "Billetera"
            3 -> "Bolsos"
            4 -> "Broches"
            5 -> "Bufandas"
            6 -> "Cinturones"
            7 -> "Collares"
            8 -> "Gafas"
            9 -> "Guantes"
            10 -> "Pulseras"
            11 -> "Relojes"
            12 -> "Sombreros"
            else -> "Desconocido"
        }
        val confidence = if (maxIndex != -1) probabilities[maxIndex] else 0.0f
        return Pair(fruitName, confidence)
    }

    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        var bitmap: Bitmap? = null
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                try {
                    val exif = ExifInterface(context.contentResolver.openInputStream(uri)!!)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    val rotatedBitmap = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                        else -> bitmap
                    }
                    bitmap = rotatedBitmap
                } catch (e: IOException) {
                    Log.e("AccesoNet", "Error processing EXIF data: ${e.message}")
                }
            }

        } catch (e: IOException) {
            Log.e("AccesoNet", "Error getting bitmap from URI: ${e.message}")
        } finally {
            try {
                inputStream?.close()
            } catch (e: IOException) {
                Log.e("AccesoNet", "Error closing input stream: ${e.message}")
            }
        }
        return bitmap
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}