package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkTimeSettingsViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _targetHours = MutableStateFlow("")
    val targetHours: StateFlow<String> = _targetHours.asStateFlow()

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    init {
        viewModelScope.launch {
            _targetHours.value = try {
                userRepository.getDefaultDailyTargetHours()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun setTargetHours(value: String) {
        _targetHours.value = value
        _justSaved.value = false
    }

    fun save() {
        viewModelScope.launch {
            try {
                userRepository.setDefaultDailyTargetHours(_targetHours.value)
                _justSaved.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort only — this simple field has no dedicated error UI yet.
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                WorkTimeSettingsViewModel(app.container.userRepository)
            }
        }
    }
}
