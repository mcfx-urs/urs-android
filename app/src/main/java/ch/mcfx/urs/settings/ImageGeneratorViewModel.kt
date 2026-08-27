package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.ImageGenRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Every value gpt-image-1's API accepts (per OpenAI's docs at implementation
// time); kept in sync with the backend's validation sets in web/imagegen.go.
val IMAGE_SIZE_OPTIONS = listOf("1024x1024", "1024x1536", "1536x1024", "auto")
val IMAGE_QUALITY_OPTIONS = listOf("low", "medium", "high", "auto")
val IMAGE_BACKGROUND_OPTIONS = listOf("transparent", "opaque", "auto")

// The same defaults the catalog product-image flow already uses, so the
// screen works unchanged out of the box while staying fully adjustable.
const val DEFAULT_IMAGE_SIZE = "1024x1024"
const val DEFAULT_IMAGE_QUALITY = "medium"
const val DEFAULT_IMAGE_BACKGROUND = "transparent"

data class ImageGeneratorFormState(
    val prompt: String = "",
    val size: String = DEFAULT_IMAGE_SIZE,
    val quality: String = DEFAULT_IMAGE_QUALITY,
    val background: String = DEFAULT_IMAGE_BACKGROUND,
)

/**
 * Settings → Image generator — a super-user-gated tool that runs a free-text
 * prompt through `gpt-image-1` and hands back the raw PNG. Nothing is stored
 * server-side; the catalog product-image flow is untouched. Same
 * `AuthTokenStore.isSuperUser` gate as the Image Review screen (the backend
 * endpoint also 403s for anyone else).
 */
class ImageGeneratorViewModel(private val repository: ImageGenRepository) : ViewModel() {

    private val _form = MutableStateFlow(ImageGeneratorFormState())
    val form: StateFlow<ImageGeneratorFormState> = _form.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _generateFailed = MutableStateFlow(false)
    val generateFailed: StateFlow<Boolean> = _generateFailed.asStateFlow()

    // Raw PNG bytes of the current result — null before the first generate
    // and after the result is deleted.
    private val _resultPng = MutableStateFlow<ByteArray?>(null)
    val resultPng: StateFlow<ByteArray?> = _resultPng.asStateFlow()

    // The MediaStore URI (as a string) once the current result has been
    // saved to the device gallery.
    private val _savedUri = MutableStateFlow<String?>(null)
    val savedUri: StateFlow<String?> = _savedUri.asStateFlow()

    fun setPrompt(value: String) = _form.update { it.copy(prompt = value) }

    fun setSize(value: String) = _form.update { it.copy(size = value) }

    fun setQuality(value: String) = _form.update { it.copy(quality = value) }

    fun setBackground(value: String) = _form.update { it.copy(background = value) }

    fun generate() {
        val form = _form.value
        if (form.prompt.isBlank() || _generating.value) return

        viewModelScope.launch {
            _generating.value = true
            _generateFailed.value = false
            _resultPng.value = null
            _savedUri.value = null
            try {
                _resultPng.value = repository.generate(form.prompt.trim(), form.size, form.quality, form.background)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _generateFailed.value = true
            } finally {
                _generating.value = false
            }
        }
    }

    fun onImageSaved(uri: String) {
        _savedUri.value = uri
    }

    fun onImageDeleted() {
        _savedUri.value = null
        _resultPng.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ImageGeneratorViewModel(app.container.imageGenRepository)
            }
        }
    }
}
