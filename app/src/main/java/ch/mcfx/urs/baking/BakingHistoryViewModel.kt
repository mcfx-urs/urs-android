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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BakingHistoryUiState {
    data object Loading : BakingHistoryUiState
    data class Data(val plans: List<BakePlanEntity>) : BakingHistoryUiState
}

/** Both completed and cancelled plans, distinguished by a status badge — this session's explicit design decision. */
class BakingHistoryViewModel(private val repository: BakingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<BakingHistoryUiState>(BakingHistoryUiState.Loading)
    val uiState: StateFlow<BakingHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePlans(listOf(BakingRepository.STATUS_COMPLETED, BakingRepository.STATUS_CANCELLED)).collect {
                _uiState.value = BakingHistoryUiState.Data(it)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                BakingHistoryViewModel(app.container.bakingRepository)
            }
        }
    }
}
