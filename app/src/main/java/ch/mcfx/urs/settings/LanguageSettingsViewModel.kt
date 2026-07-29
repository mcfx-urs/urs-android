package ch.mcfx.urs.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LanguageSettingsViewModel(private val context: Context) : ViewModel() {

    private val _language = MutableStateFlow(currentAppLanguage(context))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        setAppLanguage(context, language)
        _language.value = language
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LanguageSettingsViewModel(this[APPLICATION_KEY]!!) }
        }
    }
}
