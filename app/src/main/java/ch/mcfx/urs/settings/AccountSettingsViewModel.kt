package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.auth.AuthRepository
import ch.mcfx.urs.auth.AuthTokenStore
import ch.mcfx.urs.auth.BiometricGate

class AccountSettingsViewModel(
    private val authRepository: AuthRepository,
    tokenStore: AuthTokenStore,
    // Exposed directly (not wrapped in this ViewModel's own state) since
    // the actual BiometricPrompt ceremony needs a FragmentActivity and is
    // driven from AccountSettingsScreen itself, mirroring BiometricUnlockScreen.
    val biometricGate: BiometricGate,
) : ViewModel() {

    val userName: String? = tokenStore.userName

    fun logout() = authRepository.logout()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                AccountSettingsViewModel(app.container.authRepository, app.container.authTokenStore, app.container.biometricGate)
            }
        }
    }
}
