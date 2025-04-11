package com.ary.accesonet.view

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ary.accesonet.utils.ImageUtils
import com.ary.accesonet.viewmodel.AccesoNetViewModel
import java.io.File

@Composable
fun HomeScreen(viewModel: AccesoNetViewModel = viewModel()) {
    val context = LocalContext.current

    val file = remember { createImageFile(context) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val capturedBitmap = BitmapFactory.decodeFile(file.absolutePath)
            capturedBitmap?.let {
                val scaledBitmap = ImageUtils.scaleBitmap(it, 128)
                viewModel.setBitmap(ImageUtils.rotateBitmap(scaledBitmap, 90f))
            } ?: run {
                viewModel.setErrorMessage("Error al procesar la imagen")
            }
        } else {
            viewModel.setErrorMessage("Error al tomar la foto")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri: Uri? ->
        if (imageUri != null) {
            try {
                val originalBitmap = ImageUtils.getBitmapFromUri(context, imageUri)
                originalBitmap?.let {
                    val scaledBitmap = ImageUtils.scaleBitmap(it, 128)
                    viewModel.setBitmap(scaledBitmap)
                } ?: run {
                    viewModel.setErrorMessage("Error decoding bitmap from URI.")
                }
            } catch (e: Exception) {
                viewModel.setErrorMessage("Error al cargar la imagen de la galería: ${e.message}")
            }
        } else {
            viewModel.setErrorMessage("No se seleccionó ninguna imagen de la galería.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            cameraLauncher.launch(intent)
        } else {
            viewModel.setErrorMessage("Permiso de cámara denegado")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize() // Ocupa toda la pantalla
            .padding(16.dp), // Mantiene el padding
        horizontalAlignment = Alignment.CenterHorizontally, // Centra los elementos horizontalmente
        verticalArrangement = Arrangement.Center // Centra el contenido verticalmente
    ) {
        // Título cambiado y centrado por defecto por el Column
        Text("Clasificar Accesorio", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(24.dp)) // Espacio entre título e imagen

        // Muestra la imagen o texto placeholder (centrado por defecto)
        viewModel.bitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Accesorio a clasificar", // Descripción actualizada
                modifier = Modifier.size(250.dp) // Puedes ajustar el tamaño
            )
        } ?: Text(
            text = "Seleccione una imagen",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 100.dp) // Añade padding si no hay imagen para centrar mejor el texto
        )


        Spacer(modifier = Modifier.height(24.dp))


        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = {

                if (ImageUtils.hasCameraPermission(context)) {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    }
                    cameraLauncher.launch(intent)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }) {
                Text("Tomar Foto")
            }

            Button(onClick = {

                galleryLauncher.launch("image/*")
            }) {
                Text("Galería")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))


        Button(
            onClick = { viewModel.classifyCurrentNet(context) },
            enabled = viewModel.bitmap != null && !viewModel.isLoading
        ) {
            // Texto del botón cambiado
            Text("Clasificar Accesorio")
        }

        Spacer(modifier = Modifier.height(16.dp))


        if (viewModel.isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }


        viewModel.classificationResult?.let { (itemName, confidence) ->

            val displayedName = if (confidence < 0.5f) "Desconocido" else itemName
            val displayedConfidence = if (confidence < 0.8f) 0f else confidence
            Text("Resultado: $displayedName (${String.format("%.1f", displayedConfidence * 100)}%)")
            Spacer(modifier = Modifier.height(8.dp))
        }


        viewModel.errorMessage?.let {
            Text("Error: $it", color = MaterialTheme.colorScheme.error)
        }
    }
}


fun createImageFile(context: Context): File {

    return File(context.cacheDir, "captured_image_${System.currentTimeMillis()}.jpg").apply {
        createNewFile()
    }
}