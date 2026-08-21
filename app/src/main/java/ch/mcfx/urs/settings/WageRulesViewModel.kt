package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.UserRepository
import ch.mcfx.urs.data.WageRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Pre-filled the first time a user opens this screen (backend value blank),
// so the feature works out of the box and only needs adjusting, not filling
// in from scratch — see GitHub issue #11's worked example. AHV/ALV match
// current official Swiss employee-share rates; BVG has no sensible default
// (varies per pension plan/coordinated salary), so it starts at 0.
private val DefaultVacationPaySurchargePercent = "10.6"
private val DefaultHolidaySurchargePercent = "3.8"
private val DefaultThirteenthMonthSurchargePercent = "8.33"
private val DefaultAhvIvEoDeductionPercent = "5.3"
private val DefaultAlvDeductionPercent = "1.1"
private val DefaultSuvaNbuDeductionPercent = "1.76"
private val DefaultKtgDeductionPercent = "1.621"
private val DefaultBvgDeductionAmount = "0"

class WageRulesViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _vacationPaySurchargePercent = MutableStateFlow(DefaultVacationPaySurchargePercent)
    val vacationPaySurchargePercent: StateFlow<String> = _vacationPaySurchargePercent.asStateFlow()

    private val _holidaySurchargePercent = MutableStateFlow(DefaultHolidaySurchargePercent)
    val holidaySurchargePercent: StateFlow<String> = _holidaySurchargePercent.asStateFlow()

    private val _thirteenthMonthSurchargePercent = MutableStateFlow(DefaultThirteenthMonthSurchargePercent)
    val thirteenthMonthSurchargePercent: StateFlow<String> = _thirteenthMonthSurchargePercent.asStateFlow()

    private val _ahvIvEoDeductionPercent = MutableStateFlow(DefaultAhvIvEoDeductionPercent)
    val ahvIvEoDeductionPercent: StateFlow<String> = _ahvIvEoDeductionPercent.asStateFlow()

    private val _alvDeductionPercent = MutableStateFlow(DefaultAlvDeductionPercent)
    val alvDeductionPercent: StateFlow<String> = _alvDeductionPercent.asStateFlow()

    private val _suvaNbuDeductionPercent = MutableStateFlow(DefaultSuvaNbuDeductionPercent)
    val suvaNbuDeductionPercent: StateFlow<String> = _suvaNbuDeductionPercent.asStateFlow()

    private val _ktgDeductionPercent = MutableStateFlow(DefaultKtgDeductionPercent)
    val ktgDeductionPercent: StateFlow<String> = _ktgDeductionPercent.asStateFlow()

    private val _bvgDeductionAmount = MutableStateFlow(DefaultBvgDeductionAmount)
    val bvgDeductionAmount: StateFlow<String> = _bvgDeductionAmount.asStateFlow()

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val rules = userRepository.getWorkSettings().wageRules
                rules.vacationPaySurchargePercent?.takeIf { it.isNotBlank() }?.let { _vacationPaySurchargePercent.value = it }
                rules.holidaySurchargePercent?.takeIf { it.isNotBlank() }?.let { _holidaySurchargePercent.value = it }
                rules.thirteenthMonthSurchargePercent?.takeIf { it.isNotBlank() }?.let { _thirteenthMonthSurchargePercent.value = it }
                rules.ahvIvEoDeductionPercent?.takeIf { it.isNotBlank() }?.let { _ahvIvEoDeductionPercent.value = it }
                rules.alvDeductionPercent?.takeIf { it.isNotBlank() }?.let { _alvDeductionPercent.value = it }
                rules.suvaNbuDeductionPercent?.takeIf { it.isNotBlank() }?.let { _suvaNbuDeductionPercent.value = it }
                rules.ktgDeductionPercent?.takeIf { it.isNotBlank() }?.let { _ktgDeductionPercent.value = it }
                rules.bvgDeductionAmount?.takeIf { it.isNotBlank() }?.let { _bvgDeductionAmount.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort only — fields just stay at their defaults above.
            }
        }
    }

    fun setVacationPaySurchargePercent(value: String) = markDirty { _vacationPaySurchargePercent.value = value }
    fun setHolidaySurchargePercent(value: String) = markDirty { _holidaySurchargePercent.value = value }
    fun setThirteenthMonthSurchargePercent(value: String) = markDirty { _thirteenthMonthSurchargePercent.value = value }
    fun setAhvIvEoDeductionPercent(value: String) = markDirty { _ahvIvEoDeductionPercent.value = value }
    fun setAlvDeductionPercent(value: String) = markDirty { _alvDeductionPercent.value = value }
    fun setSuvaNbuDeductionPercent(value: String) = markDirty { _suvaNbuDeductionPercent.value = value }
    fun setKtgDeductionPercent(value: String) = markDirty { _ktgDeductionPercent.value = value }
    fun setBvgDeductionAmount(value: String) = markDirty { _bvgDeductionAmount.value = value }

    private inline fun markDirty(set: () -> Unit) {
        set()
        _justSaved.value = false
        _saveFailed.value = false
    }

    fun save() {
        viewModelScope.launch {
            try {
                userRepository.setWageRules(
                    WageRules(
                        vacationPaySurchargePercent = _vacationPaySurchargePercent.value,
                        holidaySurchargePercent = _holidaySurchargePercent.value,
                        thirteenthMonthSurchargePercent = _thirteenthMonthSurchargePercent.value,
                        ahvIvEoDeductionPercent = _ahvIvEoDeductionPercent.value,
                        alvDeductionPercent = _alvDeductionPercent.value,
                        suvaNbuDeductionPercent = _suvaNbuDeductionPercent.value,
                        ktgDeductionPercent = _ktgDeductionPercent.value,
                        bvgDeductionAmount = _bvgDeductionAmount.value,
                    ),
                )
                _justSaved.value = true
                _saveFailed.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveFailed.value = true
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                WageRulesViewModel(app.container.userRepository)
            }
        }
    }
}
