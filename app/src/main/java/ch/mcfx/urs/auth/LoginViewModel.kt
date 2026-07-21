package ch.mcfx.urs.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

enum class LoginFailure { NONE, INVALID_CREDENTIALS, CONNECTIVITY }

data class LoginFormState(
    val userName: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val failure: LoginFailure = LoginFailure.NONE,
) {
    val isValid: Boolean get() = userName.isNotBlank() && password.isNotBlank()
}

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _formState = MutableStateFlow(LoginFormState())
    val formState: StateFlow<LoginFormState> = _formState.asStateFlow()

    fun setUserName(value: String) = _formState.update { it.copy(userName = value, failure = LoginFailure.NONE) }

    fun setPassword(value: String) = _formState.update { it.copy(password = value, failure = LoginFailure.NONE) }

    fun submit() {
        val form = _formState.value
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, failure = LoginFailure.NONE) }
            try {
                repository.login(form.userName.trim(), form.password)
                // No further state update needed on success: AuthTokenStore.isLoggedIn
                // flips to true, which is what AppNavigation actually watches to leave
                // this screen — not a local "success" flag here.
            } catch (e: CancellationException) {
                throw e
            } catch (_: HttpException) {
                // Backend was reachable and responded — a real auth rejection.
                _formState.update { it.copy(submitting = false, failure = LoginFailure.INVALID_CREDENTIALS) }
            } catch (_: IOException) {
                // Request never got a response at all (no VPN/Wi-Fi to the
                // backend, timeout, etc.) — distinct from a wrong
                // password rather than collapsing into the same message.
                _formState.update { it.copy(submitting = false, failure = LoginFailure.CONNECTIVITY) }
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, failure = LoginFailure.INVALID_CREDENTIALS) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                LoginViewModel(app.container.authRepository)
            }
        }
    }
}
