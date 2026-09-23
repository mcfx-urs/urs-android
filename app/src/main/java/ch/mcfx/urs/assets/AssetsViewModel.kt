package ch.mcfx.urs.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.AssetCategory
import ch.mcfx.urs.data.AssetRepository
import ch.mcfx.urs.data.local.AssetCommentEntity
import ch.mcfx.urs.data.local.AssetComponentEntity
import ch.mcfx.urs.data.local.AssetEntity
import ch.mcfx.urs.data.local.OutboxAssetInitialComponent
import ch.mcfx.urs.data.local.publicId
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

sealed interface AssetsUiState {
    data object Loading : AssetsUiState
    data class Data(val assets: List<AssetEntity>) : AssetsUiState
}

data class NewComponentInput(
    val description: String = "",
    val manufacturer: String = "",
    val price: String = "",
    val purchaseDate: String = LocalDate.now().toString(),
    val dealer: String = "",
) {
    val isValid: Boolean get() = description.isNotBlank() && price.toDoubleOrNull() != null && purchaseDate.isNotBlank()
}

data class NewCommentInput(val text: String = "", val date: String = LocalDate.now().toString()) {
    val isValid: Boolean get() = text.isNotBlank()
}

data class AssetFormState(
    // Null = creating a new asset; set = editing this local row.
    val editingAssetId: Long? = null,
    val name: String = "",
    val category: AssetCategory? = null,
    val location: String = "",
    val status: String = AssetEntity.STATUS_ACTIVE,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    // First component, captured inline in the same create form — see
    // AssetRepository.createAsset's doc comment. Only used while creating;
    // once editing, new components go through newComponentInput below.
    val firstComponent: NewComponentInput = NewComponentInput(),
    val newComponentInput: NewComponentInput = NewComponentInput(),
    val newCommentInput: NewCommentInput = NewCommentInput(),
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isEditing: Boolean get() = editingAssetId != null

    val isValid: Boolean
        get() {
            if (name.isBlank() || category == null) return false
            if (isEditing) return true
            // Creating: the first component is all-or-nothing — either every
            // required field is filled, or none of them are (a bare grouping
            // asset with no purchase yet is also a valid create).
            val anyFilled = firstComponent.description.isNotBlank() || firstComponent.price.isNotBlank()
            return !anyFilled || firstComponent.isValid
        }
}

/**
 * Assets (mcfx-urs/urs-android#89) — list+filter, create/edit form, and the
 * editing-only components/comments sections, all in one ViewModel, same
 * shape as [ch.mcfx.urs.service.ServiceViewModel] (no separate detail VM).
 */
class AssetsViewModel(private val repository: AssetRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AssetsUiState>(AssetsUiState.Loading)
    val uiState: StateFlow<AssetsUiState> = _uiState.asStateFlow()

    // Null = no filter (show every category/status).
    private val _categoryFilter = MutableStateFlow<AssetCategory?>(null)
    val categoryFilter: StateFlow<AssetCategory?> = _categoryFilter.asStateFlow()

    private val _statusFilter = MutableStateFlow<String?>(null)
    val statusFilter: StateFlow<String?> = _statusFilter.asStateFlow()

    private val _formState = MutableStateFlow(AssetFormState())
    val formState: StateFlow<AssetFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    private val _components = MutableStateFlow<List<AssetComponentEntity>>(emptyList())
    val components: StateFlow<List<AssetComponentEntity>> = _components.asStateFlow()

    private val _comments = MutableStateFlow<List<AssetCommentEntity>>(emptyList())
    val comments: StateFlow<List<AssetCommentEntity>> = _comments.asStateFlow()

    private val _actionSheetAsset = MutableStateFlow<AssetEntity?>(null)
    val actionSheetAsset: StateFlow<AssetEntity?> = _actionSheetAsset.asStateFlow()

    private val _pendingDeleteAsset = MutableStateFlow<AssetEntity?>(null)
    val pendingDeleteAsset: StateFlow<AssetEntity?> = _pendingDeleteAsset.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeAssets(), _categoryFilter, _statusFilter) { assets, category, status ->
                assets.filter { (category == null || it.category == category) && (status == null || it.status == status) }
            }.collect { _uiState.value = AssetsUiState.Data(it) }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    fun setCategoryFilter(value: AssetCategory?) {
        _categoryFilter.value = value
    }

    fun setStatusFilter(value: String?) {
        _statusFilter.value = value
    }

    fun openForm() {
        _formState.value = AssetFormState()
        _components.value = emptyList()
        _comments.value = emptyList()
        _showForm.value = true
    }

    fun openFormForEdit(assetId: Long) {
        viewModelScope.launch {
            val data = repository.getAssetWithDetails(assetId) ?: return@launch
            _formState.value = AssetFormState(
                editingAssetId = data.asset.id,
                name = data.asset.name,
                category = data.asset.category,
                location = data.asset.location,
                status = data.asset.status,
                tags = data.asset.tags,
            )
            _components.value = data.components
            _comments.value = data.comments
            _showForm.value = true
        }
    }

    private fun refreshDetails() {
        val id = _formState.value.editingAssetId ?: return
        viewModelScope.launch {
            val data = repository.getAssetWithDetails(id) ?: return@launch
            _components.value = data.components
            _comments.value = data.comments
        }
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun openActionSheet(asset: AssetEntity) {
        _actionSheetAsset.value = asset
    }

    fun closeActionSheet() {
        _actionSheetAsset.value = null
    }

    fun requestDelete() {
        val asset = _actionSheetAsset.value ?: return
        _actionSheetAsset.value = null
        _pendingDeleteAsset.value = asset
    }

    fun cancelDelete() {
        _pendingDeleteAsset.value = null
    }

    fun confirmDelete() {
        val asset = _pendingDeleteAsset.value ?: return
        _pendingDeleteAsset.value = null
        viewModelScope.launch { repository.deleteAsset(asset.id) }
    }

    fun setName(value: String) = _formState.update { it.copy(name = value) }
    fun setCategory(value: AssetCategory) = _formState.update { it.copy(category = value) }
    fun setLocation(value: String) = _formState.update { it.copy(location = value) }
    fun setStatus(value: String) = _formState.update { it.copy(status = value) }

    fun setTagInput(value: String) = _formState.update { it.copy(tagInput = value) }
    fun addTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        _formState.update {
            if (trimmed in it.tags) it.copy(tagInput = "") else it.copy(tags = it.tags + trimmed, tagInput = "")
        }
    }
    fun removeTag(name: String) = _formState.update { it.copy(tags = it.tags - name) }

    fun setFirstComponentDescription(value: String) = _formState.update { it.copy(firstComponent = it.firstComponent.copy(description = value)) }
    fun setFirstComponentManufacturer(value: String) = _formState.update { it.copy(firstComponent = it.firstComponent.copy(manufacturer = value)) }
    fun setFirstComponentPrice(value: String) = _formState.update { it.copy(firstComponent = it.firstComponent.copy(price = value)) }
    fun setFirstComponentPurchaseDate(value: String) = _formState.update { it.copy(firstComponent = it.firstComponent.copy(purchaseDate = value)) }
    fun setFirstComponentDealer(value: String) = _formState.update { it.copy(firstComponent = it.firstComponent.copy(dealer = value)) }

    fun setNewComponentDescription(value: String) = _formState.update { it.copy(newComponentInput = it.newComponentInput.copy(description = value)) }
    fun setNewComponentManufacturer(value: String) = _formState.update { it.copy(newComponentInput = it.newComponentInput.copy(manufacturer = value)) }
    fun setNewComponentPrice(value: String) = _formState.update { it.copy(newComponentInput = it.newComponentInput.copy(price = value)) }
    fun setNewComponentPurchaseDate(value: String) = _formState.update { it.copy(newComponentInput = it.newComponentInput.copy(purchaseDate = value)) }
    fun setNewComponentDealer(value: String) = _formState.update { it.copy(newComponentInput = it.newComponentInput.copy(dealer = value)) }

    fun addComponent() {
        val asset = _formState.value.editingAssetId ?: return
        val input = _formState.value.newComponentInput
        if (!input.isValid) return
        viewModelScope.launch {
            val publicId = repository.getAssetWithDetails(asset)?.asset?.publicId ?: return@launch
            repository.addComponent(publicId, input.description, input.manufacturer, input.price, input.purchaseDate, input.dealer)
            _formState.update { it.copy(newComponentInput = NewComponentInput()) }
            refreshDetails()
        }
    }

    fun deleteComponent(component: AssetComponentEntity) {
        viewModelScope.launch {
            repository.deleteComponent(component.id)
            refreshDetails()
        }
    }

    fun setNewCommentText(value: String) = _formState.update { it.copy(newCommentInput = it.newCommentInput.copy(text = value)) }
    fun setNewCommentDate(value: String) = _formState.update { it.copy(newCommentInput = it.newCommentInput.copy(date = value)) }

    fun addComment() {
        val asset = _formState.value.editingAssetId ?: return
        val input = _formState.value.newCommentInput
        if (!input.isValid) return
        viewModelScope.launch {
            val publicId = repository.getAssetWithDetails(asset)?.asset?.publicId ?: return@launch
            repository.addComment(publicId, input.text, input.date)
            _formState.update { it.copy(newCommentInput = NewCommentInput()) }
            refreshDetails()
        }
    }

    fun deleteComment(comment: AssetCommentEntity) {
        viewModelScope.launch {
            repository.deleteComment(comment.id)
            refreshDetails()
        }
    }

    fun submit() {
        val form = _formState.value
        val category = form.category ?: return
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                val editingId = form.editingAssetId
                if (editingId != null) {
                    repository.updateAsset(editingId, form.name, category, form.location, form.status, form.tags)
                } else {
                    val initial = if (form.firstComponent.isValid) {
                        listOf(
                            OutboxAssetInitialComponent(
                                description = form.firstComponent.description,
                                manufacturer = form.firstComponent.manufacturer,
                                price = form.firstComponent.price,
                                purchaseDate = form.firstComponent.purchaseDate,
                                dealer = form.firstComponent.dealer,
                            ),
                        )
                    } else {
                        emptyList()
                    }
                    repository.createAsset(form.name, category, form.location, form.tags, initial)
                }
                _showForm.value = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                AssetsViewModel(app.container.assetRepository)
            }
        }
    }
}
