package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.TagRepository
import ch.mcfx.urs.data.alphabeticSortKey
import ch.mcfx.urs.data.remote.TagDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings → Tags screen (mcfx-urs/urs-android#103) — lists the caller's
 * full shared tag pool (Notes + Kanban, mcfx-urs/urs-backend#11) and lets
 * any one be recolored via mcfx-urs/urs-backend#15's endpoint.
 */
class TagsSettingsViewModel(private val tagRepository: TagRepository) : ViewModel() {

    init {
        // Same "warm the cache on screen entry" trigger already used by
        // ProductManagementViewModel — without it, this screen would only
        // ever show whatever another screen happened to have cached already.
        viewModelScope.launch { tagRepository.refreshFromBackend() }
    }

    val tags: StateFlow<List<TagDto>> = tagRepository.tags
        .map { tags -> tags.sortedBy { it.name.alphabeticSortKey() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editingTag = MutableStateFlow<TagDto?>(null)
    val editingTag: StateFlow<TagDto?> = _editingTag.asStateFlow()

    fun openColorPicker(tag: TagDto) {
        _editingTag.value = tag
    }

    fun closeColorPicker() {
        _editingTag.value = null
    }

    fun setColor(color: String) {
        val tag = _editingTag.value ?: return
        _editingTag.value = null
        viewModelScope.launch {
            try {
                tagRepository.updateColor(tag.name, color)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort — the row simply keeps its old color if this
                // failed server-side; the user can retry the tap.
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                TagsSettingsViewModel(app.container.tagRepository)
            }
        }
    }
}
