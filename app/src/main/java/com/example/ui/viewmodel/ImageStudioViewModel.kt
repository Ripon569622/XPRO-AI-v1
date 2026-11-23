package com.example.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AiProvider
import com.example.data.model.ImageAspectOption
import com.example.data.repository.AiRepository
import com.google.firebase.ai.type.QuotaExceededException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val prompt: String) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

class ImageStudioViewModel(
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val uiState: StateFlow<ImageGenUiState> = _uiState.asStateFlow()

    private val _selectedAspect = MutableStateFlow(ImageAspectOption.SQUARE_1_1)
    val selectedAspect: StateFlow<ImageAspectOption> = _selectedAspect.asStateFlow()

    private val _selectedProvider = MutableStateFlow(AiProvider.GEMINI)
    val selectedProvider: StateFlow<AiProvider> = _selectedProvider.asStateFlow()

    private val _referenceBitmap = MutableStateFlow<Bitmap?>(null)
    val referenceBitmap: StateFlow<Bitmap?> = _referenceBitmap.asStateFlow()

    fun setAspect(option: ImageAspectOption) {
        _selectedAspect.value = option
    }

    fun setProvider(provider: AiProvider) {
        _selectedProvider.value = provider
    }

    fun setReferenceBitmap(bitmap: Bitmap?) {
        _referenceBitmap.value = bitmap
    }

    fun generateImage(promptText: String) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = ImageGenUiState.Loading
            try {
                val bitmap = aiRepository.generateImage(
                    prompt = cleanPrompt,
                    aspectOption = _selectedAspect.value,
                    provider = _selectedProvider.value,
                    referenceBitmap = _referenceBitmap.value
                )
                _uiState.value = ImageGenUiState.Success(bitmap, cleanPrompt)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.value = ImageGenUiState.Error(
                    "Image generation timed out after 60 seconds. Please try again."
                )
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image generation quota exceeded", e)
                _uiState.value = ImageGenUiState.Error(
                    "Quota exceeded for Gemini image generation. Check Firebase Blaze plan or rate limits."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.value = ImageGenUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Result<String> {
        return try {
            val filename = "XPRO_AI_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/XPRO_AI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(IllegalStateException("Could not create MediaStore entry."))
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("Saved to Pictures/XPRO_AI/$filename")
            } else {
                @Suppress("DEPRECATION")
                val url = MediaStore.Images.Media.insertImage(
                    context.contentResolver,
                    bitmap,
                    filename,
                    "Generated with XPRO AI"
                )
                if (url != null) Result.success("Saved to Gallery")
                else Result.failure(IllegalStateException("Failed to save image"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving image", e)
            Result.failure(e)
        }
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, prompt: String) {
        try {
            val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
            val imageFile = File(imagesDir, "xpro_share_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Created with XPRO AI: $prompt")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share AI Image"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed sharing image", e)
        }
    }

    companion object {
        private const val TAG = "ImageStudioVM"

        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ImageStudioViewModel(aiRepository = AiRepository())
            }
        }
    }
}
