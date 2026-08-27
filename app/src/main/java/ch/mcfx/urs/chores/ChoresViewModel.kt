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
import ch.mcfx.urs.data.local.publicId
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChoresUiState(
    val types: List<TrackerTypeEntity> = emptyList(),
    val events: List<TrackerEventEntity> = emptyList(),
) {
    /** Non-archived types — the set pickers, filter chips and the stats strip use. */
    val activeTypes: List<TrackerTypeEntity> get() = types.filter { it.archivedAtMillis == null }

    /** Every type keyed by the id an event references (archived included, so historical events still resolve). */
    val typesByPublicId: Map<String, TrackerTypeEntity> get() = types.associateBy { it.publicId }
}

class ChoresViewModel(
    private val repository: ChoreRepository,
    private val reminderSettings: ChoreReminderSettingsStore,
) : ViewModel() {

    val uiState: StateFlow<ChoresUiState> =
        combine(repository.observeTypes(), repository.observeEvents()) { types, events ->
            ChoresUiState(types = types, events = events)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChoresUiState())

    /** Per-type "notify when overdue" opt-in, keyed by publicId. */
    val notifyTypeIds: StateFlow<Set<String>> = reminderSettings.notifyTypeIds

    fun setTypeNotifyEnabled(publicId: String, enabled: Boolean) {
        reminderSettings.setTypeNotifyEnabled(publicId, enabled)
        if (!enabled) reminderSettings.clearNotified(publicId)
    }

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /** Type publicIds the user has hidden from the calendar overlay; empty = show all. */
    private val _hiddenTypeIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenTypeIds: StateFlow<Set<String>> = _hiddenTypeIds.asStateFlow()

    init {
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    fun showMonth(target: YearMonth) {
        _month.value = target
    }

    fun previousMonth() {
        _month.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        _month.update { it.plusMonths(1) }
    }

    fun toggleTypeVisible(typeId: String) {
        _hiddenTypeIds.update { if (typeId in it) it - typeId else it + typeId }
    }

    fun createType(name: String, color: String, icon: String, calendar: String?, expectedIntervalDays: Int?) {
        viewModelScope.launch {
            repository.createType(name.trim(), color, icon, calendar?.trim()?.ifBlank { null }, expectedIntervalDays)
        }
    }

    fun updateType(localId: Long, name: String, color: String, icon: String, calendar: String?, expectedIntervalDays: Int?) {
        viewModelScope.launch {
            repository.updateType(localId, name.trim(), color, icon, calendar?.trim()?.ifBlank { null }, expectedIntervalDays)
        }
    }

    /** Builds the .ics file(s) from the currently loaded types and events. */
    fun buildIcsExport(exportAll: Boolean): IcsExport {
        val state = uiState.value
        return buildChoresIcs(types = state.types, events = state.events, exportAll = exportAll)
    }

    fun markExported(localIds: List<Long>) {
        if (localIds.isEmpty()) return
        viewModelScope.launch { repository.markExported(localIds) }
    }

    fun archiveType(localId: Long) {
        viewModelScope.launch { repository.archiveType(localId) }
    }

    fun logEvent(typeId: String, occurredOn: String, occurredAt: String?, note: String?) {
        viewModelScope.launch { repository.logEvent(typeId, occurredOn, occurredAt?.ifBlank { null }, note?.ifBlank { null }) }
    }

    fun updateEvent(localId: Long, typeId: String, occurredOn: String, occurredAt: String?, note: String?) {
        viewModelScope.launch {
            repository.updateEvent(localId, typeId, occurredOn, occurredAt?.ifBlank { null }, note?.ifBlank { null })
        }
    }

    fun deleteEvent(localId: Long) {
        viewModelScope.launch { repository.deleteEvent(localId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ChoresViewModel(app.container.choreRepository, app.container.choreReminderSettingsStore)
            }
        }
    }
}
