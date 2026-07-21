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

// Two-step confirm (requestRestart -> confirmRestart) rather than a
// Material AlertDialog — this design system has no dialog component
// anywhere else in the app, so AdminScreen renders the confirm/cancel row
// inline instead of introducing one just for this.
class AdminViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _confirmingRestart = MutableStateFlow(false)
    val confirmingRestart: StateFlow<Boolean> = _confirmingRestart.asStateFlow()

    private val _restarting = MutableStateFlow(false)
    val restarting: StateFlow<Boolean> = _restarting.asStateFlow()

    private val _restartRequested = MutableStateFlow(false)
    val restartRequested: StateFlow<Boolean> = _restartRequested.asStateFlow()

    private val _restartFailed = MutableStateFlow(false)
    val restartFailed: StateFlow<Boolean> = _restartFailed.asStateFlow()

    fun requestRestart() {
        _confirmingRestart.value = true
    }

    fun cancelRestart() {
        _confirmingRestart.value = false
    }

    fun confirmRestart() {
        _confirmingRestart.value = false
        _restarting.value = true
        _restartRequested.value = false
        _restartFailed.value = false
        viewModelScope.launch {
            try {
                userRepository.restartServer()
                _restartRequested.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _restartFailed.value = true
            } finally {
                _restarting.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                AdminViewModel(app.container.userRepository)
            }
        }
    }
}
