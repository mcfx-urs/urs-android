package ch.mcfx.urs.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.chores.ChoreOrderStore
import ch.mcfx.urs.chores.ChoreReminderSettingsStore
import ch.mcfx.urs.chores.IcsExport
import ch.mcfx.urs.chores.buildChoresIcs
import ch.mcfx.urs.data.ChoreRepository
import ch.mcfx.urs.data.local.TrackerDomainEntity
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

data class JournalUiState(
    val domains: List<TrackerDomainEntity> = emptyList(),
    val types: List<TrackerTypeEntity> = emptyList(),
    val events: List<TrackerEventEntity> = emptyList(),
) {
    val activeTypes: List<TrackerTypeEntity> get() = types.filter { it.archivedAtMillis == null }
    val archivedTypes: List<TrackerTypeEntity> get() = types.filter { it.archivedAtMillis != null }
    val typesByPublicId: Map<String, TrackerTypeEntity> get() = types.associateBy { it.publicId }
    val domainsByPublicId: Map<String, TrackerDomainEntity> get() = domains.associateBy { it.publicId }
}

/**
 * Generalizes [ch.mcfx.urs.chores.ChoresViewModel] into domain-organized
 * Journal (GitHub issue #83) — same [ChoreRepository]/[ChoreOrderStore]/
 * [ChoreReminderSettingsStore] instances, since types/events/their reminder
 * and order preferences are the same underlying rows Chores still shows
 * unchanged during the transition; only the domain grouping is new.
 */
class JournalViewModel(
    private val repository: ChoreRepository,
    private val reminderSettings: ChoreReminderSettingsStore,
    private val orderStore: ChoreOrderStore,
) : ViewModel() {

    val uiState: StateFlow<JournalUiState> =
        combine(
            repository.observeDomains(),
            repository.observeTypes(),
            repository.observeEvents(),
            orderStore.order,
        ) { domains, types, events, order ->
            JournalUiState(domains = domains, types = applyOrder(types, order), events = events)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    /** Per-type "notify when overdue" opt-in, keyed by publicId — same store Chores' type editor uses. */
    val notifyTypeIds: StateFlow<Set<String>> = reminderSettings.notifyTypeIds

    fun setTypeNotifyEnabled(publicId: String, enabled: Boolean) {
        reminderSettings.setTypeNotifyEnabled(publicId, enabled)
        if (!enabled) reminderSettings.clearNotified(publicId)
    }

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /** Type publicIds hidden from the calendar; empty = show all. Local-only, last state only (no backend sync). */
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

    fun showAllTypes() {
        _hiddenTypeIds.value = emptySet()
    }

    fun hideAllTypes(publicIds: Collection<String>) {
        _hiddenTypeIds.value = publicIds.toSet()
    }

    fun toggleDomainVisible(typeIds: List<String>) {
        val allHidden = typeIds.isNotEmpty() && typeIds.all { it in _hiddenTypeIds.value }
        _hiddenTypeIds.update { current -> if (allHidden) current - typeIds.toSet() else current + typeIds.toSet() }
    }

    // --- Domains ---

    fun createDomain(name: String, color: String, icon: String) {
        viewModelScope.launch { repository.createDomain(name.trim(), color, icon) }
    }

    // --- Types ---

    fun createType(name: String, color: String, icon: String, domainId: String, calendar: String?, expectedIntervalDays: Int?) {
        viewModelScope.launch {
            repository.createType(name.trim(), color, icon, calendar?.trim()?.ifBlank { null }, expectedIntervalDays, domainId)
        }
    }

    fun updateType(
        localId: Long,
        name: String,
        color: String,
        icon: String,
        domainId: String,
        calendar: String?,
        expectedIntervalDays: Int?,
    ) {
        viewModelScope.launch {
            repository.updateType(localId, name.trim(), color, icon, calendar?.trim()?.ifBlank { null }, expectedIntervalDays, domainId)
        }
    }

    fun archiveType(localId: Long) {
        viewModelScope.launch { repository.archiveType(localId) }
    }

    fun reactivateType(localId: Long) {
        viewModelScope.launch { repository.reactivateType(localId) }
    }

    /** Persists the manually dragged order (a list of publicIds) — shared with Chores' own stats strip. */
    fun reorderTypes(publicIds: List<String>) {
        orderStore.setOrder(publicIds)
    }

    // --- Events ---

    fun logEvent(typeId: String, occurredOn: String, occurredOnEnd: String?, occurredAt: String?, occurredAtEnd: String?, note: String?) {
        viewModelScope.launch {
            repository.logEvent(
                typeId = typeId,
                occurredOn = occurredOn,
                occurredAt = occurredAt?.ifBlank { null },
                note = note?.ifBlank { null },
                occurredOnEnd = occurredOnEnd?.ifBlank { null },
                occurredAtEnd = occurredAtEnd?.ifBlank { null },
            )
        }
    }

    fun updateEvent(
        localId: Long,
        typeId: String,
        occurredOn: String,
        occurredOnEnd: String?,
        occurredAt: String?,
        occurredAtEnd: String?,
        note: String?,
    ) {
        viewModelScope.launch {
            repository.updateEvent(
                localId = localId,
                typeId = typeId,
                occurredOn = occurredOn,
                occurredAt = occurredAt?.ifBlank { null },
                note = note?.ifBlank { null },
                occurredOnEnd = occurredOnEnd?.ifBlank { null },
                occurredAtEnd = occurredAtEnd?.ifBlank { null },
            )
        }
    }

    fun deleteEvent(localId: Long) {
        viewModelScope.launch { repository.deleteEvent(localId) }
    }

    /** Builds the .ics file(s) from the currently loaded types and events — same export Chores' own share icon triggers. */
    fun buildIcsExport(exportAll: Boolean): IcsExport {
        val state = uiState.value
        return buildChoresIcs(types = state.types, events = state.events, exportAll = exportAll)
    }

    fun markExported(localIds: List<Long>) {
        if (localIds.isEmpty()) return
        viewModelScope.launch { repository.markExported(localIds) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                JournalViewModel(
                    app.container.choreRepository,
                    app.container.choreReminderSettingsStore,
                    app.container.choreOrderStore,
                )
            }
        }
    }
}

/** Types with a stored position sort by it (ties keep DB order); unordered types are appended, DB order preserved. */
private fun applyOrder(types: List<TrackerTypeEntity>, order: List<String>): List<TrackerTypeEntity> {
    if (order.isEmpty()) return types
    val rank = order.withIndex().associate { (index, id) -> id to index }
    return types.sortedBy { rank[it.publicId] ?: Int.MAX_VALUE }
}
