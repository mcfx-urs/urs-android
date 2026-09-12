package ch.mcfx.urs.kanban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.KanbanBoardDetail
import ch.mcfx.urs.data.KanbanRepository
import ch.mcfx.urs.data.NoteRepository
import ch.mcfx.urs.data.local.KanbanCardEntity
import ch.mcfx.urs.data.local.KanbanChecklistItemEntity
import ch.mcfx.urs.data.local.KanbanColumnEntity
import ch.mcfx.urs.data.local.NoteEntity
import ch.mcfx.urs.data.local.publicId
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface KanbanBoardDetailUiState {
    data object Loading : KanbanBoardDetailUiState
    data object NotFound : KanbanBoardDetailUiState
    data class Data(val detail: KanbanBoardDetail) : KanbanBoardDetailUiState
}

data class KanbanColumnFormState(
    // null = creating a new column; set = renaming this local row.
    val editingColumnId: Long? = null,
    val name: String = "",
    val submitting: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

/** Card editor bottom sheet state — the Android equivalent of the web `KanbanCardDialog`. */
data class KanbanCardFormState(
    // null = creating a new card in [columnId]; set = editing this local row.
    val localId: Long? = null,
    val columnId: String = "",
    val title: String = "",
    val description: String = "",
    val dueDateEnabled: Boolean = false,
    val dueDate: String = "",
    val priority: String = KanbanCardEntity.PRIORITY_MEDIUM,
    val linkedNoteId: String? = null,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val tagSuggestions: List<String> = emptyList(),
    val checklistInput: String = "",
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = title.isNotBlank()
    val isEditing: Boolean get() = localId != null
}

class KanbanBoardDetailViewModel(
    private val repository: KanbanRepository,
    private val noteRepository: NoteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<KanbanBoardDetailUiState>(KanbanBoardDetailUiState.Loading)
    val uiState: StateFlow<KanbanBoardDetailUiState> = _uiState.asStateFlow()

    private val _showColumnForm = MutableStateFlow(false)
    val showColumnForm: StateFlow<Boolean> = _showColumnForm.asStateFlow()

    private val _columnFormState = MutableStateFlow(KanbanColumnFormState())
    val columnFormState: StateFlow<KanbanColumnFormState> = _columnFormState.asStateFlow()

    // Long-press → Rename/Delete, same shape as ShoppingListsViewModel's own action sheet.
    private val _actionSheetColumn = MutableStateFlow<KanbanColumnEntity?>(null)
    val actionSheetColumn: StateFlow<KanbanColumnEntity?> = _actionSheetColumn.asStateFlow()

    private val _columnNotEmptyError = MutableStateFlow(false)
    val columnNotEmptyError: StateFlow<Boolean> = _columnNotEmptyError.asStateFlow()

    private val _cardEditor = MutableStateFlow<KanbanCardFormState?>(null)
    val cardEditor: StateFlow<KanbanCardFormState?> = _cardEditor.asStateFlow()

    private val _availableNotes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val availableNotes: StateFlow<List<NoteEntity>> = _availableNotes.asStateFlow()

    /**
     * The live checklist for whichever card [cardEditor] currently has open
     * — derived from [uiState] rather than duplicated into [KanbanCardFormState]
     * itself, so a toggle/add/delete (each its own instant repository write,
     * not bundled into the card form's own submit) reflects back into the
     * open sheet the moment the underlying Room row changes, the same way
     * every other screen in this app reacts to its own repository Flow.
     */
    val cardEditorChecklist: StateFlow<List<KanbanChecklistItemEntity>> =
        combine(_cardEditor, _uiState) { editor, state ->
            val localId = editor?.localId ?: return@combine emptyList()
            val detail = (state as? KanbanBoardDetailUiState.Data)?.detail ?: return@combine emptyList()
            detail.columns.flatMap { it.cards }.firstOrNull { it.card.id == localId }?.checklist.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadBoard(boardId: String) {
        viewModelScope.launch {
            val localId = repository.resolveLocalBoardId(boardId)
            if (localId == null) {
                _uiState.value = KanbanBoardDetailUiState.NotFound
                return@launch
            }
            repository.refreshBoard(boardId)
            repository.observeBoard(localId).collect { detail ->
                _uiState.value = if (detail == null) KanbanBoardDetailUiState.NotFound else KanbanBoardDetailUiState.Data(detail)
            }
        }
    }

    // --- Columns

    fun openCreateColumnForm() {
        _columnFormState.value = KanbanColumnFormState()
        _showColumnForm.value = true
    }

    fun openRenameColumnForm(column: KanbanColumnEntity) {
        _columnFormState.value = KanbanColumnFormState(editingColumnId = column.id, name = column.name)
        _actionSheetColumn.value = null
        _showColumnForm.value = true
    }

    fun closeColumnForm() {
        _showColumnForm.value = false
    }

    fun setColumnName(value: String) = _columnFormState.update { it.copy(name = value) }

    fun submitColumnForm() {
        val form = _columnFormState.value
        val boardId = (uiState.value as? KanbanBoardDetailUiState.Data)?.detail?.board?.publicId
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _columnFormState.update { it.copy(submitting = true) }
            try {
                val editingId = form.editingColumnId
                if (editingId != null) {
                    repository.renameColumn(editingId, form.name.trim())
                } else if (boardId != null) {
                    repository.createColumn(boardId, form.name.trim())
                }
                _showColumnForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _columnFormState.update { it.copy(submitting = false) }
            }
        }
    }

    fun openColumnActionSheet(column: KanbanColumnEntity) {
        _actionSheetColumn.value = column
    }

    fun closeColumnActionSheet() {
        _actionSheetColumn.value = null
    }

    fun deleteColumn(columnLocalId: Long) {
        _actionSheetColumn.value = null
        viewModelScope.launch {
            val deleted = repository.deleteColumn(columnLocalId)
            if (!deleted) _columnNotEmptyError.value = true
        }
    }

    fun dismissColumnNotEmptyError() {
        _columnNotEmptyError.value = false
    }

    fun moveColumn(columnLocalId: Long, targetIndex: Int) {
        viewModelScope.launch { repository.moveColumn(columnLocalId, targetIndex) }
    }

    // --- Cards

    fun openCreateCardEditor(columnId: String) {
        _cardEditor.value = KanbanCardFormState(columnId = columnId)
        loadAvailableNotes()
    }

    fun openCardEditor(card: KanbanCardEntity, tags: List<String>) {
        _cardEditor.value = KanbanCardFormState(
            localId = card.id,
            columnId = card.columnId,
            title = card.title,
            description = card.description,
            dueDateEnabled = card.dueDate != null,
            dueDate = card.dueDate ?: LocalDate.now().toString(),
            priority = card.priority,
            linkedNoteId = card.linkedNoteId,
            tags = tags,
        )
        loadAvailableNotes()
    }

    fun closeCardEditor() {
        _cardEditor.value = null
    }

    private fun loadAvailableNotes() {
        viewModelScope.launch {
            _availableNotes.value = runCatching { noteRepository.observeActive().first() }.getOrNull()?.map { it.note }.orEmpty()
        }
    }

    fun setCardTitle(value: String) = _cardEditor.update { it?.copy(title = value, submitFailed = false) }

    fun setCardDescription(value: String) = _cardEditor.update { it?.copy(description = value) }

    fun setCardDueDateEnabled(value: Boolean) = _cardEditor.update {
        it?.copy(dueDateEnabled = value, dueDate = it.dueDate.ifBlank { LocalDate.now().toString() })
    }

    fun setCardDueDate(value: String) = _cardEditor.update { it?.copy(dueDate = value) }

    fun setCardPriority(value: String) = _cardEditor.update { it?.copy(priority = value) }

    fun setCardLinkedNoteId(value: String?) = _cardEditor.update { it?.copy(linkedNoteId = value) }

    fun setCardTagInput(value: String) {
        _cardEditor.update { it?.copy(tagInput = value) }
        viewModelScope.launch {
            val existing = _cardEditor.value?.tags.orEmpty()
            val suggestions = if (value.isBlank()) emptyList() else repository.suggestTags(value).filterNot { it in existing }
            _cardEditor.update { it?.copy(tagSuggestions = suggestions) }
        }
    }

    fun addCardTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        _cardEditor.update {
            val tags = if (trimmed in it?.tags.orEmpty()) it?.tags.orEmpty() else it?.tags.orEmpty() + trimmed
            it?.copy(tags = tags, tagInput = "", tagSuggestions = emptyList())
        }
    }

    fun removeCardTag(tag: String) = _cardEditor.update { it?.copy(tags = it.tags - tag) }

    fun submitCardEditor() {
        val form = _cardEditor.value ?: return
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _cardEditor.update { it?.copy(submitting = true, submitFailed = false) }
            try {
                val dueDate = if (form.dueDateEnabled) form.dueDate else null
                val localId = form.localId
                if (localId != null) {
                    repository.updateCard(localId, form.title.trim(), form.description, dueDate, form.priority, form.linkedNoteId, form.tags)
                } else {
                    repository.createCard(form.columnId, form.title.trim(), form.description, dueDate, form.priority, form.linkedNoteId, form.tags)
                }
                _cardEditor.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _cardEditor.update { it?.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun deleteCurrentCard() {
        val localId = _cardEditor.value?.localId ?: return
        _cardEditor.value = null
        viewModelScope.launch { repository.deleteCard(localId) }
    }

    fun moveCard(cardLocalId: Long, targetColumnId: String, targetIndex: Int) {
        viewModelScope.launch { repository.moveCard(cardLocalId, targetColumnId, targetIndex) }
    }

    fun addChecklistItem() {
        val form = _cardEditor.value ?: return
        val cardId = currentCardPublicId(form.localId) ?: return
        val text = form.checklistInput.trim()
        if (text.isEmpty()) return
        _cardEditor.update { it?.copy(checklistInput = "") }
        viewModelScope.launch { repository.addChecklistItem(cardId, text) }
    }

    fun setChecklistInput(value: String) = _cardEditor.update { it?.copy(checklistInput = value) }

    fun toggleChecklistItem(item: KanbanChecklistItemEntity) {
        viewModelScope.launch { repository.toggleChecklistItem(item.id, !item.done) }
    }

    fun deleteChecklistItem(item: KanbanChecklistItemEntity) {
        viewModelScope.launch { repository.deleteChecklistItem(item.id) }
    }

    private fun currentCardPublicId(localCardId: Long?): String? {
        localCardId ?: return null
        val detail = (uiState.value as? KanbanBoardDetailUiState.Data)?.detail ?: return null
        return detail.columns.flatMap { it.cards }.firstOrNull { it.card.id == localCardId }?.card?.publicId
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                KanbanBoardDetailViewModel(app.container.kanbanRepository, app.container.noteRepository)
            }
        }
    }
}
