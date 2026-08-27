package ch.mcfx.urs.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.NoteRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteDetailFormState(
    // null = creating a new note; set = editing/viewing this local row.
    val localId: Long? = null,
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val tagSuggestions: List<String> = emptyList(),
    val reminderEnabled: Boolean = false,
    val reminderDate: String = LocalDate.now().toString(),
    // "HH:mm", same TimeInput convention as WorkTimeFormState.
    val reminderTime: String = "",
    val status: String = NoteRepository.STATUS_ACTIVE,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
    // Flips true once a submit/delete has completed — the screen navigates
    // back on this, same "state flag, not a callback" shape as
    // WorkTimeViewModel's showForm.
    val finished: Boolean = false,
) {
    val isValid: Boolean
        get() = title.isNotBlank() && (!reminderEnabled || (reminderDate.isNotBlank() && reminderTime.isNotBlank()))

    /** The reminder as epoch millis when it's enabled and both fields parse, else null — used by the note export actions. */
    val reminderMillis: Long?
        get() = if (reminderEnabled) dateAndTimeToMillis(reminderDate, reminderTime) else null
}

class NoteDetailViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _formState = MutableStateFlow(NoteDetailFormState(loading = false))
    val formState: StateFlow<NoteDetailFormState> = _formState.asStateFlow()

    fun startNew() {
        _formState.value = NoteDetailFormState(loading = false)
    }

    /** [noteId] is a note's publicId (real serverId, or a not-yet-synced stand-in) — same convention as BakePlanDetailViewModel, needed since a reminder notification's deep link only ever has that, never the local row id directly. */
    fun loadForEdit(noteId: String) {
        _formState.value = NoteDetailFormState(loading = true)
        viewModelScope.launch {
            val localId = repository.resolveLocalNoteId(noteId)
            if (localId == null) {
                _formState.value = NoteDetailFormState(loading = false, notFound = true)
                return@launch
            }
            val noteWithTags = repository.observeNote(localId).first()
            if (noteWithTags == null) {
                _formState.value = NoteDetailFormState(loading = false, notFound = true)
                return@launch
            }
            val note = noteWithTags.note
            val (date, time) = note.reminderAtMillis?.let { millisToDateAndTime(it) } ?: (LocalDate.now().toString() to "")
            _formState.value = NoteDetailFormState(
                localId = note.id,
                title = note.title,
                content = note.content,
                tags = noteWithTags.tags.map { it.tagName },
                reminderEnabled = note.reminderAtMillis != null,
                reminderDate = date,
                reminderTime = time,
                status = note.status,
                loading = false,
            )
        }
    }

    fun setTitle(value: String) = _formState.update { it.copy(title = value, submitFailed = false) }

    fun setContent(value: String) = _formState.update { it.copy(content = value) }

    fun setReminderEnabled(value: Boolean) = _formState.update { it.copy(reminderEnabled = value) }

    fun setReminderDate(value: String) = _formState.update { it.copy(reminderDate = value) }

    fun setReminderTime(value: String) = _formState.update { it.copy(reminderTime = value) }

    fun setTagInput(value: String) {
        _formState.update { it.copy(tagInput = value) }
        viewModelScope.launch {
            val existing = _formState.value.tags
            val suggestions = if (value.isBlank()) emptyList() else repository.suggestTags(value).filterNot { it in existing }
            _formState.update { it.copy(tagSuggestions = suggestions) }
        }
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        _formState.update {
            val tags = if (trimmed in it.tags) it.tags else it.tags + trimmed
            it.copy(tags = tags, tagInput = "", tagSuggestions = emptyList())
        }
    }

    fun removeTag(tag: String) = _formState.update { it.copy(tags = it.tags - tag) }

    fun submit() {
        val form = _formState.value
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                val reminderMillis = if (form.reminderEnabled) dateAndTimeToMillis(form.reminderDate, form.reminderTime) else null
                val localId = form.localId
                if (localId != null) {
                    repository.updateNote(localId, form.title.trim(), form.content, reminderMillis, form.tags)
                } else {
                    repository.createNote(form.title.trim(), form.content, reminderMillis, form.tags)
                }
                _formState.update { it.copy(finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    /** Optimistic: updates the form's own [NoteDetailFormState.status] immediately, doesn't leave the screen. */
    fun toggleStatus() {
        val localId = _formState.value.localId ?: return
        val newStatus = if (_formState.value.status == NoteRepository.STATUS_ACTIVE) {
            NoteRepository.STATUS_COMPLETED
        } else {
            NoteRepository.STATUS_ACTIVE
        }
        _formState.update { it.copy(status = newStatus) }
        viewModelScope.launch { repository.setStatus(localId, newStatus) }
    }

    fun delete() {
        val localId = _formState.value.localId ?: return
        viewModelScope.launch {
            repository.deleteNote(localId)
            _formState.update { it.copy(finished = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                NoteDetailViewModel(app.container.noteRepository)
            }
        }
    }
}

private val ReminderTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun millisToDateAndTime(millis: Long): Pair<String, String> {
    val dateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    return dateTime.toLocalDate().toString() to dateTime.toLocalTime().format(ReminderTimeFormatter)
}

private fun dateAndTimeToMillis(date: String, time: String): Long? {
    val localDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    val localTime = runCatching { LocalTime.parse(time, ReminderTimeFormatter) }.getOrNull() ?: return null
    return LocalDateTime.of(localDate, localTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
