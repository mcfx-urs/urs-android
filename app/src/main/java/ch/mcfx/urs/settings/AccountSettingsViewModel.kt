package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.auth.AuthRepository
import ch.mcfx.urs.auth.AuthTokenStore

class AccountSettingsViewModel(
    private val authRepository: AuthRepository,
    tokenStore: AuthTokenStore,
) : ViewModel() {

    val userName: String? = tokenStore.userName

    fun logout() = authRepository.logout()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                AccountSettingsViewModel(app.container.authRepository, app.container.authTokenStore)
            }
        }
    }
}
