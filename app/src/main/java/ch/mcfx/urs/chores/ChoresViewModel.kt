package ch.mcfx.urs.chores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.ChoreRepository
import ch.mcfx.urs.data.local.TrackerEventEntity
import ch.mcfx.urs.data.local.TrackerTypeEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChoresUiState(
    val types: List<TrackerTypeEntity> = emptyList(),
    val events: List<TrackerEventEntity> = emptyList(),
) {
    /** Non-archived types, the set pickers and the type-filter offer. */
    val activeTypes: List<TrackerTypeEntity> get() = types.filter { it.archivedAtMillis == null }
}

class ChoresViewModel(private val repository: ChoreRepository) : ViewModel() {

    val uiState: StateFlow<ChoresUiState> =
        combine(repository.observeTypes(), repository.observeEvents()) { types, events ->
            ChoresUiState(types = types, events = events)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChoresUiState())

    init {
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    fun createType(name: String, color: String, icon: String) {
        viewModelScope.launch { repository.createType(name, color, icon) }
    }

    fun logEvent(typeId: String, occurredOn: String, occurredAt: String?, note: String?) {
        viewModelScope.launch { repository.logEvent(typeId, occurredOn, occurredAt, note) }
    }

    fun updateEvent(localId: Long, typeId: String, occurredOn: String, occurredAt: String?, note: String?) {
        viewModelScope.launch { repository.updateEvent(localId, typeId, occurredOn, occurredAt, note) }
    }

    fun deleteEvent(localId: Long) {
        viewModelScope.launch { repository.deleteEvent(localId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ChoresViewModel(app.container.choreRepository)
            }
        }
    }
}
