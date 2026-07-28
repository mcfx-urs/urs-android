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
import ch.mcfx.urs.data.local.BakePlanStepEntity
import ch.mcfx.urs.data.local.publicId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface BakePlanDetailUiState {
    data object Loading : BakePlanDetailUiState
    data class Data(val plan: BakePlanEntity, val steps: List<BakePlanStepEntity>) : BakePlanDetailUiState
    data object NotFound : BakePlanDetailUiState
}

/** Checklist screen for one plan's steps — check off / snooze (no cascade) / cancel the whole plan. */
class BakePlanDetailViewModel(
    private val repository: BakingRepository,
    private val planId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BakePlanDetailUiState>(BakePlanDetailUiState.Loading)
    val uiState: StateFlow<BakePlanDetailUiState> = _uiState.asStateFlow()

    private val _showSnoozeFor = MutableStateFlow<BakePlanStepEntity?>(null)
    val showSnoozeFor: StateFlow<BakePlanStepEntity?> = _showSnoozeFor.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeAllPlans(), repository.observeSteps(planId)) { plans, steps ->
                val plan = plans.find { it.publicId == planId }
                if (plan == null) {
                    BakePlanDetailUiState.NotFound
                } else {
                    BakePlanDetailUiState.Data(plan, steps.sortedBy { it.stepIndex })
                }
            }.collect { _uiState.value = it }
        }
    }

    fun toggleStepDone(step: BakePlanStepEntity) {
        viewModelScope.launch { repository.markStepDone(step.id, done = step.doneAtMillis == null) }
    }

    fun openSnooze(step: BakePlanStepEntity) {
        _showSnoozeFor.value = step
    }

    fun closeSnooze() {
        _showSnoozeFor.value = null
    }

    fun confirmSnooze(newTimeMillis: Long) {
        val step = _showSnoozeFor.value ?: return
        viewModelScope.launch { repository.snoozeStep(step.id, newTimeMillis) }
        _showSnoozeFor.value = null
    }

    fun cancelPlan() {
        val plan = (uiState.value as? BakePlanDetailUiState.Data)?.plan ?: return
        viewModelScope.launch { repository.cancelPlan(plan.id) }
    }

    companion object {
        fun factory(planId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                BakePlanDetailViewModel(app.container.bakingRepository, planId)
            }
        }
    }
}
