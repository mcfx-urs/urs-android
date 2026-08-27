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
import kotlinx.coroutines.delay
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

    private val _serverBackUp = MutableStateFlow(false)
    val serverBackUp: StateFlow<Boolean> = _serverBackUp.asStateFlow()

    private val _checkTimedOut = MutableStateFlow(false)
    val checkTimedOut: StateFlow<Boolean> = _checkTimedOut.asStateFlow()

    // Current backend log level, null until the initial load resolves.
    private val _currentLogLevel = MutableStateFlow<String?>(null)
    val currentLogLevel: StateFlow<String?> = _currentLogLevel.asStateFlow()

    private val _logLevelUpdateFailed = MutableStateFlow(false)
    val logLevelUpdateFailed: StateFlow<Boolean> = _logLevelUpdateFailed.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _currentLogLevel.value = userRepository.getLogLevel()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Leave it null — the dropdown just shows no selection until
                // the user picks one, which still applies fine.
            }
        }
    }

    // No two-step confirm, unlike restart: a log-level change doesn't
    // interrupt the service, so the inline-confirm pattern doesn't apply.
    fun setLogLevel(level: String) {
        _logLevelUpdateFailed.value = false
        viewModelScope.launch {
            try {
                userRepository.setLogLevel(level)
                _currentLogLevel.value = level
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _logLevelUpdateFailed.value = true
            }
        }
    }

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
        _serverBackUp.value = false
        _checkTimedOut.value = false
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
            if (_restartRequested.value) {
                pollUntilBackUp()
            }
        }
    }

    // Polls the public root route (see UrsApi.ping) until it responds,
    // confirming the restart actually completed — without this, "the
    // server will be back shortly" had no way to ever resolve to anything
    // on screen, it just sat there regardless of what actually happened.
    private suspend fun pollUntilBackUp() {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            try {
                userRepository.ping()
                _serverBackUp.value = true
                return
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Expected while the pod is still restarting — keep polling.
            }
        }
        _checkTimedOut.value = true
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3_000L
        private const val POLL_TIMEOUT_MS = 90_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                AdminViewModel(app.container.userRepository)
            }
        }
    }
}
