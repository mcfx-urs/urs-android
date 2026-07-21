package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.auth.AuthRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

// Mirrors urs-backend's own minPasswordLength (web/auth.go) — kept as-is
// per scoping decision, not a new/stricter rule invented here.
private const val MIN_NEW_PASSWORD_LENGTH = 8

enum class ChangePasswordFailure { NONE, WRONG_CURRENT_PASSWORD, CONNECTIVITY, UNKNOWN }

data class ChangePasswordFormState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val submitting: Boolean = false,
    val failure: ChangePasswordFailure = ChangePasswordFailure.NONE,
    val success: Boolean = false,
) {
    val isValid: Boolean
        get() = currentPassword.isNotBlank() &&
            newPassword.length >= MIN_NEW_PASSWORD_LENGTH &&
            newPassword == confirmPassword
}

/**
 * . Same submit/failure-mapping shape as [ch.mcfx.urs.auth.LoginViewModel]
 * (HttpException vs. IOException distinguished the same way), plus a
 * distinct WRONG_CURRENT_PASSWORD case since — unlike login — this form
 * already knows the caller's identity and just needs to say which field
 * was wrong.
 */
class ChangePasswordViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _formState = MutableStateFlow(ChangePasswordFormState())
    val formState: StateFlow<ChangePasswordFormState> = _formState.asStateFlow()

    fun setCurrentPassword(value: String) =
        _formState.update { it.copy(currentPassword = value, failure = ChangePasswordFailure.NONE) }

    fun setNewPassword(value: String) =
        _formState.update { it.copy(newPassword = value, failure = ChangePasswordFailure.NONE) }

    fun setConfirmPassword(value: String) =
        _formState.update { it.copy(confirmPassword = value, failure = ChangePasswordFailure.NONE) }

    fun submit() {
        val form = _formState.value
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, failure = ChangePasswordFailure.NONE) }
            try {
                repository.changePassword(form.currentPassword, form.newPassword)
                _formState.value = ChangePasswordFormState(success = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val failure = if (e.code() == 401) {
                    ChangePasswordFailure.WRONG_CURRENT_PASSWORD
                } else {
                    ChangePasswordFailure.UNKNOWN
                }
                _formState.update { it.copy(submitting = false, failure = failure) }
            } catch (_: IOException) {
                _formState.update { it.copy(submitting = false, failure = ChangePasswordFailure.CONNECTIVITY) }
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, failure = ChangePasswordFailure.UNKNOWN) }
            }
        }
    }

    fun reset() {
        _formState.value = ChangePasswordFormState()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ChangePasswordViewModel(app.container.authRepository)
            }
        }
    }
}
