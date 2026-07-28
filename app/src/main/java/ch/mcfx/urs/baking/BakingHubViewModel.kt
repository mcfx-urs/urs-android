package ch.mcfx.urs.baking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.BakingRepository
import ch.mcfx.urs.data.local.BakePlanEntity
import ch.mcfx.urs.data.sourdoughBreadTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BakingHubUiState {
    data object Loading : BakingHubUiState
    data class Data(val plans: List<BakePlanEntity>) : BakingHubUiState
}

data class BakingPlanFormState(
    val date: String = "",
    val time: String = "",
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = date.isNotBlank() && time.isNotBlank()
}

/** Only one template is seeded for v1 (Sourdough Bread) — no template picker, just an anchor time. */
class BakingHubViewModel(private val repository: BakingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<BakingHubUiState>(BakingHubUiState.Loading)
    val uiState: StateFlow<BakingHubUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(BakingPlanFormState())
    val formState: StateFlow<BakingPlanFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePlans(listOf(BakingRepository.STATUS_ACTIVE)).collect {
                _uiState.value = BakingHubUiState.Data(it)
            }
        }
    }

    fun openCreateForm() {
        _formState.value = BakingPlanFormState()
        _showForm.value = true
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun setDate(value: String) = _formState.update { it.copy(date = value) }
    fun setTime(value: String) = _formState.update { it.copy(time = value) }

    fun submit() {
        val form = _formState.value
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                val anchor = LocalDateTime.of(LocalDate.parse(form.date), LocalTime.parse(form.time))
                repository.createPlan(sourdoughBreadTemplate, anchor)
                _showForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                BakingHubViewModel(app.container.bakingRepository)
            }
        }
    }
}
