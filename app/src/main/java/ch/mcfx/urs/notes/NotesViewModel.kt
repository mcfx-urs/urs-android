package ch.mcfx.urs.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
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

private const val TAG_FILTER_KEY = "tagFilter"

/** Active-notes hub, with an in-memory tag filter — small local lists, no need to push filtering into the DB query. */
class NotesViewModel(private val repository: NoteRepository, private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val _uiState = MutableStateFlow<NotesUiState>(NotesUiState.Loading)
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    // SavedStateHandle-backed (GitHub issue #84) rather than a plain
    // MutableStateFlow - survives even if the NavBackStackEntry-scoped
    // NotesViewModel instance itself gets recreated across a back-stack pop
    // (opening a note, then pressing back), plus process death/recreation.
    val tagFilter: StateFlow<String?> = savedStateHandle.getStateFlow(TAG_FILTER_KEY, null)

    init {
        viewModelScope.launch {
            repository.observeActive().collect { notes -> _uiState.value = NotesUiState.Data(notes) }
        }
    }

    /**
     * Called from [NotesHubScreen] on every entry, not just once from
     * [init] — the ViewModel survives bottom-nav tab switches
     * (restoreState/saveState), so an init-only fetch never re-runs and
     * edits made on another device stay invisible until a full app
     * restart creates a fresh ViewModel.
     */
    fun refresh() {
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    fun setTagFilter(tag: String?) {
        savedStateHandle[TAG_FILTER_KEY] = tag
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                NotesViewModel(app.container.noteRepository, createSavedStateHandle())
            }
        }
    }
}
