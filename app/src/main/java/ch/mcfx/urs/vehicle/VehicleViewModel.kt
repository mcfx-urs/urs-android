package ch.mcfx.urs.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.VehicleRepository
import ch.mcfx.urs.data.VehicleType
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.data.remote.FuelDto
import ch.mcfx.urs.data.remote.VehiclePayload
import ch.mcfx.urs.data.toRaw
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface VehicleUiState {
    data object Loading : VehicleUiState
    data class Data(val vehicles: List<VehicleEntity>, val fuelTypes: List<FuelDto>) : VehicleUiState
}

// Distinguishes why a submit/delete failed so the screen can show a
// specific message — same HttpException/IOException split already used by
// ch.mcfx.urs.settings.ChangePasswordViewModel, plus a delete-specific
// conflict case for the backend's 409 (vehicle still has fill/odometer/
// service entries).
enum class VehicleFailure { NONE, CONNECTIVITY, HAS_ENTRIES, UNKNOWN }

data class VehicleFormState(
    // Null = creating a new vehicle; set = editing this existing row.
    val editingVehicleId: String? = null,
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val fuelId: String? = null,
    val vehicleType: VehicleType = VehicleType.CAR,
    val engineCode: String = "",
    val color: String = "",
    val vin: String = "",
    val registrationNumber: String = "",
    val typeApprovalNumber: String = "",
    val displacementCcm: String = "",
    val powerKw: String = "",
    val powerPs: String = "",
    val weightKg: String = "",
    val firstRegistrationDate: String = "",
    val lastMfkDate: String = "",
    val submitting: Boolean = false,
    val submitFailure: VehicleFailure = VehicleFailure.NONE,
) {
    val isValid: Boolean
        get() = brand.isNotBlank() && model.isNotBlank() && year.isNotBlank() && fuelId != null
}

class VehicleViewModel(
    private val repository: VehicleRepository,
    private val fuelRepository: FuelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<VehicleUiState>(VehicleUiState.Loading)
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(VehicleFormState())
    val formState: StateFlow<VehicleFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    private val _actionSheetVehicle = MutableStateFlow<VehicleEntity?>(null)
    val actionSheetVehicle: StateFlow<VehicleEntity?> = _actionSheetVehicle.asStateFlow()

    private val _pendingDeleteVehicle = MutableStateFlow<VehicleEntity?>(null)
    val pendingDeleteVehicle: StateFlow<VehicleEntity?> = _pendingDeleteVehicle.asStateFlow()

    private val _deleteFailure = MutableStateFlow(VehicleFailure.NONE)
    val deleteFailure: StateFlow<VehicleFailure> = _deleteFailure.asStateFlow()

    private var fuelTypesCache: List<FuelDto> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeVehicles().collect { vehicles ->
                _uiState.value = VehicleUiState.Data(vehicles, fuelTypesCache)
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                fuelTypesCache = fuelRepository.getFuelTypes()
                val current = _uiState.value
                if (current is VehicleUiState.Data) {
                    _uiState.value = current.copy(fuelTypes = fuelTypesCache)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort — the vehicle list itself still comes from the
                // Room cache regardless of whether fuel types loaded.
            }
            // Best-effort cache warm; VehicleRepository deliberately lets
            // network/HTTP failures propagate for its mutating calls, so the
            // refresh has to be guarded here or an offline open crashes.
            runCatching { repository.refreshFromBackend() }
        }
    }

    fun openForm() {
        _formState.value = VehicleFormState()
        _showForm.value = true
    }

    fun openFormForEdit(vehicleId: String) {
        viewModelScope.launch {
            val vehicle = repository.getVehicleById(vehicleId) ?: return@launch
            _formState.value = VehicleFormState(
                editingVehicleId = vehicle.id,
                brand = vehicle.brand,
                model = vehicle.model,
                year = vehicle.year,
                fuelId = vehicle.fuelId,
                vehicleType = vehicle.vehicleType,
                engineCode = vehicle.engineCode.orEmpty(),
                color = vehicle.color.orEmpty(),
                vin = vehicle.vin.orEmpty(),
                registrationNumber = vehicle.registrationNumber.orEmpty(),
                typeApprovalNumber = vehicle.typeApprovalNumber.orEmpty(),
                displacementCcm = vehicle.displacementCcm.orEmpty(),
                powerKw = vehicle.powerKw.orEmpty(),
                powerPs = vehicle.powerPs.orEmpty(),
                weightKg = vehicle.weightKg.orEmpty(),
                firstRegistrationDate = vehicle.firstRegistrationDate.orEmpty(),
                lastMfkDate = vehicle.lastMfkDate.orEmpty(),
            )
            _showForm.value = true
        }
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun openActionSheet(vehicle: VehicleEntity) {
        _actionSheetVehicle.value = vehicle
    }

    fun closeActionSheet() {
        _actionSheetVehicle.value = null
    }

    fun requestDelete() {
        val vehicle = _actionSheetVehicle.value ?: return
        _actionSheetVehicle.value = null
        _deleteFailure.value = VehicleFailure.NONE
        _pendingDeleteVehicle.value = vehicle
    }

    fun cancelDelete() {
        _pendingDeleteVehicle.value = null
    }

    fun confirmDelete() {
        val vehicle = _pendingDeleteVehicle.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteVehicle(vehicle.id)
                _pendingDeleteVehicle.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                _deleteFailure.value = if (e.code() == 409) VehicleFailure.HAS_ENTRIES else VehicleFailure.UNKNOWN
            } catch (_: IOException) {
                _deleteFailure.value = VehicleFailure.CONNECTIVITY
            } catch (_: Exception) {
                _deleteFailure.value = VehicleFailure.UNKNOWN
            }
        }
    }

    fun setBrand(value: String) = _formState.update { it.copy(brand = value) }
    fun setModel(value: String) = _formState.update { it.copy(model = value) }
    fun setYear(value: String) = _formState.update { it.copy(year = value) }
    fun setFuelId(value: String) = _formState.update { it.copy(fuelId = value) }
    fun setVehicleType(value: VehicleType) = _formState.update { it.copy(vehicleType = value) }
    fun setEngineCode(value: String) = _formState.update { it.copy(engineCode = value) }
    fun setColor(value: String) = _formState.update { it.copy(color = value) }
    fun setVin(value: String) = _formState.update { it.copy(vin = value) }
    fun setRegistrationNumber(value: String) = _formState.update { it.copy(registrationNumber = value) }
    fun setTypeApprovalNumber(value: String) = _formState.update { it.copy(typeApprovalNumber = value) }
    fun setDisplacementCcm(value: String) = _formState.update { it.copy(displacementCcm = value) }
    fun setPowerKw(value: String) = _formState.update { it.copy(powerKw = value) }
    fun setPowerPs(value: String) = _formState.update { it.copy(powerPs = value) }
    fun setWeightKg(value: String) = _formState.update { it.copy(weightKg = value) }
    fun setFirstRegistrationDate(value: String) = _formState.update { it.copy(firstRegistrationDate = value) }
    fun setLastMfkDate(value: String) = _formState.update { it.copy(lastMfkDate = value) }

    fun submit() {
        val form = _formState.value
        if (!form.isValid || form.submitting) return

        val payload = VehiclePayload(
            fuelId = form.fuelId.orEmpty(),
            brand = form.brand,
            model = form.model,
            year = form.year,
            engineCode = form.engineCode,
            vehicleType = form.vehicleType.toRaw(),
            color = form.color,
            vin = form.vin,
            registrationNumber = form.registrationNumber,
            typeApprovalNumber = form.typeApprovalNumber,
            displacementCcm = form.displacementCcm,
            powerKw = form.powerKw,
            powerPs = form.powerPs,
            weightKg = form.weightKg,
            firstRegistrationDate = form.firstRegistrationDate,
            lastMfkDate = form.lastMfkDate,
        )

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailure = VehicleFailure.NONE) }
            try {
                val editingId = form.editingVehicleId
                if (editingId != null) {
                    repository.updateVehicle(editingId, payload)
                } else {
                    repository.createVehicle(payload)
                }
                _showForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                _formState.update { it.copy(submitting = false, submitFailure = VehicleFailure.CONNECTIVITY) }
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailure = VehicleFailure.UNKNOWN) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                VehicleViewModel(app.container.vehicleRepository, app.container.fuelRepository)
            }
        }
    }
}
