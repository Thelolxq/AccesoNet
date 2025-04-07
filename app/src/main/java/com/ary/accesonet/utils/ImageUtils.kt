package com.ary.accesonet.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object ImageUtils {

    private const val TAG = "AccesoNetUtils"
    private const val MODEL_FILENAME = "accesonet_model.tflite"
    private const val LABELS_FILENAME = "labels.txt"


    private const val MODEL_INPUT_WIDTH = 128
    private const val MODEL_INPUT_HEIGHT = 128
    private const val BYTES_PER_CHANNEL_FLOAT32 = 4
    private const val INPUT_CHANNELS = 3

    /**
     * Rota un Bitmap.
     */
    fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Escala un Bitmap manteniendo la relación de aspecto, asegurando que la dimensión
     * más grande no exceda maxSize (a menos que ya sea más pequeña).
     * Útil para pre-escalado y reducir uso de memoria.
     */
    fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val scaleFactor = maxSize.toFloat() / maxOf(width, height)

        // Solo escalar si es necesario (si alguna dimensión es mayor que maxSize)
        if (scaleFactor < 1.0) {
            val newWidth = (width * scaleFactor).toInt()
            val newHeight = (height * scaleFactor).toInt()
            // Usar filtro=true para mejor calidad
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        // Devolver original si no necesita escalado
        return bitmap
    }

    /**
     * Preprocesa el Bitmap para la entrada del modelo TFLite:
     * 1. Redimensiona a MODEL_INPUT_WIDTH x MODEL_INPUT_HEIGHT.
     * 2. Normaliza los valores de píxeles a Float [0, 1].
     * 3. Lo convierte a ByteBuffer.
     */
    private fun preprocessImageForTFLite(bitmap: Bitmap): ByteBuffer {
        // 1. Redimensionar a las dimensiones exactas del modelo
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT, true)

        // 2. Calcular tamaño del ByteBuffer y asignarlo
        val inputSize = 1 * MODEL_INPUT_HEIGHT * MODEL_INPUT_WIDTH * INPUT_CHANNELS * BYTES_PER_CHANNEL_FLOAT32
        val byteBuffer = ByteBuffer.allocateDirect(inputSize).apply {
            order(ByteOrder.nativeOrder()) // Usar el orden de bytes nativo del dispositivo
            rewind() // Asegurarse de empezar desde el principio
        }

        // 3. Extraer píxeles y cargar en ByteBuffer
        val pixels = IntArray(MODEL_INPUT_WIDTH * MODEL_INPUT_HEIGHT)
        resizedBitmap.getPixels(pixels, 0, MODEL_INPUT_WIDTH, 0, 0, MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT)

        for (pixelValue in pixels) {
            // Extraer R, G, B (ignorando Alpha)
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            // --- INICIO DE CAMBIOS ---

            // ELIMINA ESTAS LÍNEAS (o coméntalas):
            // val normalizedRed = r / 255.0f
            // val normalizedGreen = g / 255.0f
            // val normalizedBlue = b / 255.0f

            // Poner en el ByteBuffer los valores originales [0-255] como Float
            // El modelo TFLite se encargará de reescalar internamente
            byteBuffer.putFloat(r.toFloat()) // <--- CAMBIADO
            byteBuffer.putFloat(g.toFloat()) // <--- CAMBIADO
            byteBuffer.putFloat(b.toFloat()) // <--- CAMBIADO

            // --- FIN DE CAMBIOS ---
        }
        byteBuffer.rewind() // Asegurar que el buffer esté listo para leer por TFLite
        return byteBuffer
    }

    /**
     * Carga el modelo TFLite desde los assets de forma eficiente.
     * Asegura el cierre del FileInputStream.
     */
    @Throws(IOException::class)
    private fun loadModelFile(context: Context): ByteBuffer {
        val assetManager = context.assets
        assetManager.openFd(MODEL_FILENAME).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }

    /**
     * Carga la lista de etiquetas desde un archivo en assets.
     * Espera una etiqueta por línea.
     */
    @Throws(IOException::class)
    private fun loadLabels(context: Context): List<String> {
        val labels = mutableListOf<String>()
        context.assets.open(LABELS_FILENAME).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    labels.add(line!!)
                }
            }
        }
        return labels
    }

    /**
     * Ejecuta la clasificación en el Bitmap proporcionado usando el modelo TFLite.
     * Cierra el intérprete después de usarlo.
     * Carga las etiquetas desde assets.
     */
    fun classifyNet(bitmap: Bitmap, context: Context): Pair<String, Float>? { // Devolver nullable
        try {
            // Cargar modelo y etiquetas
            val modelBuffer = loadModelFile(context)
            val labels = loadLabels(context)
            val expectedLabelCount = labels.size // Número esperado de salidas del modelo

            // Crear y usar el intérprete (se cierra automáticamente)
            Interpreter(modelBuffer).use { interpreter ->

                // Preprocesar la imagen
                val input = preprocessImageForTFLite(bitmap)

                // Preparar buffer de salida
                // Asegurarse que el tamaño coincida con las etiquetas cargadas
                val output = Array(1) { FloatArray(expectedLabelCount) }

                // Ejecutar inferencia
                interpreter.run(input, output)

                // Post-procesar resultado
                val probabilities = output[0]
                val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

                return if (maxIndex != -1 && maxIndex < labels.size) {
                    val accessoryName = labels[maxIndex] // Obtener nombre desde la lista cargada
                    val confidence = probabilities[maxIndex]
                    Log.d(TAG, "Clasificación: $accessoryName ($confidence)")
                    Pair(accessoryName, confidence)
                } else {
                    Log.w(TAG, "No se pudo determinar la clase o índice fuera de rango: $maxIndex")
                    Pair("Desconocido", 0.0f) // O devolver null si prefieres
                }
            }
        } catch (e: Exception) { // Capturar cualquier excepción (IO, TFLite, etc.)
            Log.e(TAG, "Error durante la clasificación", e)
            // Propagar el error o devolver un estado de error
            // throw IOException("Error al clasificar: ${e.localizedMessage}", e) // Opción 1: Relanzar
            return null // Opción 2: Devolver null para indicar fallo
        }
    }

    /**
     * Obtiene un Bitmap desde una Uri, intentando corregir la orientación usando EXIF.
     * Corrige el problema de lectura de EXIF.
     */
    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        var inputStreamForExif: InputStream? = null
        var inputStreamForBitmap: InputStream? = null
        var bitmap: Bitmap? = null

        try {
            // 1. Abrir stream para leer EXIF
            inputStreamForExif = context.contentResolver.openInputStream(uri)
            val orientation = if (inputStreamForExif != null) {
                // Usar la librería androidx.exifinterface
                val exif = ExifInterface(inputStreamForExif)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } else {
                ExifInterface.ORIENTATION_NORMAL
            }

            // 2. Abrir stream OTRA VEZ para decodificar el Bitmap
            inputStreamForBitmap = context.contentResolver.openInputStream(uri)
            if (inputStreamForBitmap != null) {
                bitmap = BitmapFactory.decodeStream(inputStreamForBitmap)
            }

            // 3. Rotar si es necesario
            if (bitmap != null) {
                bitmap = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    // ORIENTATION_FLIP_HORIZONTAL, ORIENTATION_FLIP_VERTICAL, etc. podrían necesitar Matrix más complejas si son relevantes
                    else -> bitmap // No necesita rotación
                }
            } else {
                Log.w(TAG, "No se pudo decodificar el bitmap desde la URI: $uri")
            }

        } catch (e: IOException) {
            Log.e(TAG, "Error obteniendo bitmap desde URI: $uri", e)
            bitmap = null // Asegurar que se devuelve null en caso de error
        } finally {
            // 4. Cerrar AMBOS streams
            try {
                inputStreamForExif?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error cerrando stream EXIF", e)
            }
            try {
                inputStreamForBitmap?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error cerrando stream Bitmap", e)
            }
        }
        return bitmap
    }

    /**
     * Comprueba si la app tiene permiso para usar la cámara.
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}