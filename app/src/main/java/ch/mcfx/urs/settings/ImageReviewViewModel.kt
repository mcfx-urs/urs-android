package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.remote.CatalogImageDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings → Admin → Image Review — the super-user-gated approve/reject
 * queue for AI-generated catalog images. A pending image stays out
 * of [CatalogRepository.getImages]' shared reuse pool (every product/
 * category picker) until approved here.
 */
class ImageReviewViewModel(private val repository: CatalogRepository) : ViewModel() {

    private val _images = MutableStateFlow<List<CatalogImageDto>>(emptyList())
    val images: StateFlow<List<CatalogImageDto>> = _images.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _actionSheetImage = MutableStateFlow<CatalogImageDto?>(null)
    val actionSheetImage: StateFlow<CatalogImageDto?> = _actionSheetImage.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                _images.value = repository.getPendingImages()
                _loadFailed.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _loadFailed.value = true
            }
        }
    }

    fun openActionSheet(image: CatalogImageDto) {
        _actionSheetImage.value = image
    }

    fun closeActionSheet() {
        _actionSheetImage.value = null
    }

    /** Approve the action-sheet image — moves it into the shared reuse pool. */
    fun approve() {
        val image = _actionSheetImage.value ?: return
        _actionSheetImage.value = null
        viewModelScope.launch {
            try {
                repository.approveImage(image.id)
                _images.update { list -> list.filterNot { it.id == image.id } }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort, same as ProductManagementViewModel's delete
                // actions — the row simply stays visible if it failed, the
                // super user can retry the long-press.
            }
        }
    }

    /** Reject (delete) the action-sheet image. */
    fun reject() {
        val image = _actionSheetImage.value ?: return
        _actionSheetImage.value = null
        viewModelScope.launch {
            try {
                repository.rejectImage(image.id)
                _images.update { list -> list.filterNot { it.id == image.id } }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort, same as approve().
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ImageReviewViewModel(app.container.catalogRepository)
            }
        }
    }
}
