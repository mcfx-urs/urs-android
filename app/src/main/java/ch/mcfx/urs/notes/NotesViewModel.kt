package ch.mcfx.urs.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.NoteRepository
import ch.mcfx.urs.data.local.NoteWithTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NotesUiState {
    data object Loading : NotesUiState
    data class Data(val notes: List<NoteWithTags>) : NotesUiState
}

/** Active-notes hub, with an in-memory tag filter — small local lists, no need to push filtering into the DB query. */
class NotesViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<NotesUiState>(NotesUiState.Loading)
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    private val _tagFilter = MutableStateFlow<String?>(null)
    val tagFilter: StateFlow<String?> = _tagFilter.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeActive().collect { notes -> _uiState.value = NotesUiState.Data(notes) }
        }
    }

    fun setTagFilter(tag: String?) {
        _tagFilter.value = tag
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                NotesViewModel(app.container.noteRepository)
            }
        }
    }
}
