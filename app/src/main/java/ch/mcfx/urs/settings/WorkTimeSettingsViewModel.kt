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

    private val _employmentPercent = MutableStateFlow("")
    val employmentPercent: StateFlow<String> = _employmentPercent.asStateFlow()

    private val _hourlyWage = MutableStateFlow("")
    val hourlyWage: StateFlow<String> = _hourlyWage.asStateFlow()

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val settings = userRepository.getWorkSettings()
                _targetHours.value = settings.defaultDailyTargetHours
                _employmentPercent.value = settings.employmentPercent
                _hourlyWage.value = settings.hourlyWage
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort only — fields just stay empty, same as before any save.
            }
        }
    }

    fun setTargetHours(value: String) {
        _targetHours.value = value
        _justSaved.value = false
        _saveFailed.value = false
    }

    fun setEmploymentPercent(value: String) {
        _employmentPercent.value = value
        _justSaved.value = false
        _saveFailed.value = false
    }

    fun setHourlyWage(value: String) {
        _hourlyWage.value = value
        _justSaved.value = false
        _saveFailed.value = false
    }

    fun save() {
        viewModelScope.launch {
            try {
                userRepository.setDefaultDailyTargetHours(_targetHours.value)
                userRepository.setEmploymentPercent(_employmentPercent.value)
                userRepository.setHourlyWage(_hourlyWage.value)
                _justSaved.value = true
                _saveFailed.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveFailed.value = true
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
