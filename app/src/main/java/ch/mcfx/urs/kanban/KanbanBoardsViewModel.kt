package ch.mcfx.urs.kanban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.KanbanRepository
import ch.mcfx.urs.data.local.KanbanBoardEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface KanbanBoardsUiState {
    data object Loading : KanbanBoardsUiState
    data class Data(val boards: List<KanbanBoardEntity>) : KanbanBoardsUiState
}

data class KanbanBoardFormState(
    // null = creating a new board; set = renaming this local row.
    val editingBoardId: Long? = null,
    val name: String = "",
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

class KanbanBoardsViewModel(private val repository: KanbanRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<KanbanBoardsUiState>(KanbanBoardsUiState.Loading)
    val uiState: StateFlow<KanbanBoardsUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(KanbanBoardFormState())
    val formState: StateFlow<KanbanBoardFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    // Long-press → Rename/Delete, same shape as ShoppingListsViewModel's own action sheet.
    private val _actionSheetBoard = MutableStateFlow<KanbanBoardEntity?>(null)
    val actionSheetBoard: StateFlow<KanbanBoardEntity?> = _actionSheetBoard.asStateFlow()

    private val _pendingDeleteBoard = MutableStateFlow<KanbanBoardEntity?>(null)
    val pendingDeleteBoard: StateFlow<KanbanBoardEntity?> = _pendingDeleteBoard.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeBoards().collect { _uiState.value = KanbanBoardsUiState.Data(it) }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    fun openCreateForm() {
        _formState.value = KanbanBoardFormState()
        _showForm.value = true
    }

    fun openRenameForm(board: KanbanBoardEntity) {
        _formState.value = KanbanBoardFormState(editingBoardId = board.id, name = board.name)
        _actionSheetBoard.value = null
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
                val editingId = form.editingBoardId
                if (editingId != null) {
                    repository.renameBoard(editingId, form.name.trim())
                } else {
                    repository.createBoard(form.name.trim())
                }
                _showForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun openActionSheet(board: KanbanBoardEntity) {
        _actionSheetBoard.value = board
    }

    fun closeActionSheet() {
        _actionSheetBoard.value = null
    }

    fun toggleFavorite() {
        val board = _actionSheetBoard.value ?: return
        _actionSheetBoard.value = null
        viewModelScope.launch { repository.setBoardFavorite(board.id, !board.isFavorite) }
    }

    fun requestDelete() {
        val board = _actionSheetBoard.value ?: return
        _actionSheetBoard.value = null
        _pendingDeleteBoard.value = board
    }

    fun cancelDelete() {
        _pendingDeleteBoard.value = null
    }

    fun confirmDelete() {
        val board = _pendingDeleteBoard.value ?: return
        _pendingDeleteBoard.value = null
        viewModelScope.launch { repository.deleteBoard(board.id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                KanbanBoardsViewModel(app.container.kanbanRepository)
            }
        }
    }
}
