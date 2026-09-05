package ch.mcfx.urs.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.UserRepository
import ch.mcfx.urs.data.local.InventoryEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.UserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface InventoriesUiState {
    data object Loading : InventoriesUiState
    data class Data(val inventories: List<InventoryEntity>) : InventoriesUiState
}

data class InventoryFormState(
    // null = creating a new inventory; set = renaming this local row.
    val editingInventoryId: Long? = null,
    val name: String = "",
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

/** Share-sheet state for one inventory — see [ch.mcfx.urs.ui.components.UrsShareSheet]. */
data class InventoryShareState(
    val inventory: InventoryEntity? = null,
    val members: List<UserDto> = emptyList(),
    val sharedUserIds: Set<String> = emptySet(),
)

/** Mirrors [ch.mcfx.urs.shoppinglist.ShoppingListsViewModel] exactly — Inventory now has the identical multi-instance-container shape List always had. */
class InventoriesViewModel(
    private val repository: InventoryRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InventoriesUiState>(InventoriesUiState.Loading)
    val uiState: StateFlow<InventoriesUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(InventoryFormState())
    val formState: StateFlow<InventoryFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    // Long-press → Rename/Delete/Share, same shape as ShoppingListsViewModel's own action sheet.
    private val _actionSheetInventory = MutableStateFlow<InventoryEntity?>(null)
    val actionSheetInventory: StateFlow<InventoryEntity?> = _actionSheetInventory.asStateFlow()

    private val _pendingDeleteInventory = MutableStateFlow<InventoryEntity?>(null)
    val pendingDeleteInventory: StateFlow<InventoryEntity?> = _pendingDeleteInventory.asStateFlow()

    private val _shareState = MutableStateFlow(InventoryShareState())
    val shareState: StateFlow<InventoryShareState> = _shareState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeInventories().collect { _uiState.value = InventoriesUiState.Data(it) }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    fun openCreateForm() {
        _formState.value = InventoryFormState()
        _showForm.value = true
    }

    fun openRenameForm(inventory: InventoryEntity) {
        _formState.value = InventoryFormState(editingInventoryId = inventory.id, name = inventory.name)
        _actionSheetInventory.value = null
        _showForm.value = true
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun setName(value: String) = _formState.update { it.copy(name = value) }

    fun submit() {
        val form = _formState.value
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                val editingId = form.editingInventoryId
                if (editingId != null) {
                    repository.renameInventory(editingId, form.name.trim())
                } else {
                    repository.createInventory(form.name.trim())
                }
                // Both paths are local-only writes that return instantly —
                // no network round-trip to wait on, so the form can close
                // right away.
                _showForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun openActionSheet(inventory: InventoryEntity) {
        _actionSheetInventory.value = inventory
    }

    fun closeActionSheet() {
        _actionSheetInventory.value = null
    }

    fun toggleFavorite() {
        val inventory = _actionSheetInventory.value ?: return
        _actionSheetInventory.value = null
        viewModelScope.launch { repository.setInventoryFavorite(inventory.id, !inventory.isFavorite) }
    }

    fun requestDelete() {
        val inventory = _actionSheetInventory.value ?: return
        _actionSheetInventory.value = null
        _pendingDeleteInventory.value = inventory
    }

    fun cancelDelete() {
        _pendingDeleteInventory.value = null
    }

    fun confirmDelete() {
        val inventory = _pendingDeleteInventory.value ?: return
        _pendingDeleteInventory.value = null
        viewModelScope.launch { repository.deleteInventory(inventory.id) }
    }

    fun openShareSheet(inventory: InventoryEntity) {
        _actionSheetInventory.value = null
        val serverId = inventory.serverId ?: return
        viewModelScope.launch {
            try {
                val members = userRepository.getAllUsers()
                val shares = repository.getInventoryShares(serverId)
                _shareState.value = InventoryShareState(
                    inventory = inventory,
                    members = members,
                    sharedUserIds = shares.map { it.userId }.toSet(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: sheet simply doesn't open if the members/shares fetch failed.
            }
        }
    }

    fun closeShareSheet() {
        _shareState.value = InventoryShareState()
    }

    fun toggleShare(userId: String, currentlyShared: Boolean) {
        val inventory = _shareState.value.inventory ?: return
        val inventoryId = inventory.publicId
        viewModelScope.launch {
            try {
                if (currentlyShared) {
                    repository.removeInventoryShare(inventoryId, userId)
                } else {
                    repository.shareInventory(inventoryId, userId)
                }
                _shareState.update {
                    it.copy(
                        sharedUserIds = if (currentlyShared) it.sharedUserIds - userId else it.sharedUserIds + userId,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: the checkbox simply keeps its last-known state if the request failed.
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                InventoriesViewModel(app.container.inventoryRepository, app.container.userRepository)
            }
        }
    }
}
