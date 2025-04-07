package com.ary.accesonet.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ary.accesonet.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccesoNetViewModel(application: Application) : AndroidViewModel(application) {

    private val _bitmap: MutableState<Bitmap?> = mutableStateOf(null)
    val bitmap: Bitmap? get() = _bitmap.value

    private val _classificationResult: MutableState<Pair<String, Float>?> = mutableStateOf(null)
    val classificationResult: Pair<String, Float>? get() = _classificationResult.value

    private val _errorMessage: MutableState<String?> = mutableStateOf(null)
    val errorMessage: String? get() = _errorMessage.value

    private val _isLoading: MutableState<Boolean> = mutableStateOf(false)
    val isLoading: Boolean get() = _isLoading.value

    fun setBitmap(newBitmap: Bitmap) {
        _bitmap.value = newBitmap
        _classificationResult.value = null
        _errorMessage.value = null
    }

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    fun classifyCurrentNet(context: Context) {
        val currentBitmap = _bitmap.value
        if (currentBitmap == null) {
            setErrorMessage("No se ha capturado ninguna imagen")
            return
        }



        _isLoading.value = true
        _classificationResult.value = null
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = ImageUtils.classifyNet(currentBitmap, context)
                withContext(Dispatchers.Main) {
                    _classificationResult.value = result
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setErrorMessage("Error al clasificar el accesorio: ${e.message}")
                    _isLoading.value = false
                }
            }
        }
    }

}