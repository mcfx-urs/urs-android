package ch.mcfx.urs.worktime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.UserRepository
import ch.mcfx.urs.data.WorkTimeRepository
import ch.mcfx.urs.data.local.WorkTimeEntryWithBreaks
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WorkTimeUiState {
    data object Loading : WorkTimeUiState
    data class Data(
        val entries: List<WorkTimeEntryWithBreaks>,
        val userDefaultTargetHours: String,
    ) : WorkTimeUiState
}

private val breakDraftIds = AtomicLong()

// Form times are entered as "HH:mm" (no seconds picker in this simple text
// form) and normalized to "HH:mm:ss" only at submit time, matching what the
// backend's TIME columns and WorkTimeCalculations both expect.
data class BreakDraft(val id: Long = breakDraftIds.incrementAndGet(), val startTime: String = "", val endTime: String = "")

data class WorkTimeFormState(
    val date: String = LocalDate.now().toString(),
    val workStart: String = "",
    val workEnd: String = "",
    val targetDailyHours: String = "",
    val breaks: List<BreakDraft> = emptyList(),
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean
        get() = date.isNotBlank() && isValidTimeInput(workStart) && isValidTimeInput(workEnd) &&
            breaks.all { isValidTimeInput(it.startTime) && isValidTimeInput(it.endTime) }
}

class WorkTimeViewModel(
    private val repository: WorkTimeRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkTimeUiState>(WorkTimeUiState.Loading)
    val uiState: StateFlow<WorkTimeUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(WorkTimeFormState())
    val formState: StateFlow<WorkTimeFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    init {
        // Entries are a Room-backed Flow so the history screen has something
        // to show on a cold start with no connectivity — load() below only
        // refreshes the cache opportunistically.
        viewModelScope.launch {
            repository.observeEntries().collect { entries ->
                _uiState.update { state ->
                    val targetHours = (state as? WorkTimeUiState.Data)?.userDefaultTargetHours ?: ""
                    WorkTimeUiState.Data(entries, targetHours)
                }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { repository.refreshFromBackend() }
        viewModelScope.launch {
            val targetHours = try {
                userRepository.getDefaultDailyTargetHours()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "" // best-effort hint, not critical
            }
            _uiState.update { state ->
                val entries = (state as? WorkTimeUiState.Data)?.entries ?: emptyList()
                WorkTimeUiState.Data(entries, targetHours)
            }
        }
    }

    fun openForm() {
        _formState.value = WorkTimeFormState()
        _showForm.value = true
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun setDate(value: String) = _formState.update { it.copy(date = value) }

    fun setWorkStart(value: String) = _formState.update { it.copy(workStart = value) }

    fun setWorkEnd(value: String) = _formState.update { it.copy(workEnd = value) }

    fun setTargetDailyHours(value: String) = _formState.update { it.copy(targetDailyHours = value) }

    /** @return the new break's id, so the caller can move keyboard focus onto its start-time field. */
    fun addBreak(): Long {
        val draft = BreakDraft()
        _formState.update { it.copy(breaks = it.breaks + draft) }
        return draft.id
    }

    fun removeBreak(id: Long) = _formState.update { state -> state.copy(breaks = state.breaks.filterNot { it.id == id }) }

    fun setBreakStart(id: Long, value: String) = _formState.update { state ->
        state.copy(breaks = state.breaks.map { if (it.id == id) it.copy(startTime = value) else it })
    }

    fun setBreakEnd(id: Long, value: String) = _formState.update { state ->
        state.copy(breaks = state.breaks.map { if (it.id == id) it.copy(endTime = value) else it })
    }

    fun submit() {
        val form = _formState.value
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                repository.createEntry(
                    date = form.date,
                    workStart = form.workStart.withSeconds(),
                    workEnd = form.workEnd.withSeconds(),
                    targetDailyHours = form.targetDailyHours,
                    breaks = form.breaks.map { it.startTime.withSeconds() to it.endTime.withSeconds() },
                )
                // createEntry is a local-only write and returns instantly —
                // no network round-trip to wait on, so the form can close
                // right away. A later sync failure surfaces via the entry's
                // own pending/failed badge (see WorkTimeScreen), not here.
                _showForm.value = false
            } catch (e: CancellationException) {
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
                WorkTimeViewModel(app.container.workTimeRepository, app.container.userRepository)
            }
        }
    }
}

// Submit is only reachable once isValidTimeInput has confirmed "HH:mm", so
// appending seconds unconditionally is safe.
private fun String.withSeconds(): String = "$this:00"
