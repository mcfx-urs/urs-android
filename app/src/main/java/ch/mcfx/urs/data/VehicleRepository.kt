package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.VehicleDao
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.remote.VehicleDto
import ch.mcfx.urs.data.remote.VehiclePayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Vehicle create/edit/delete — deliberately direct/awaited REST, not the
 * Fill/WorkTimeEntry offline-outbox pattern: a delete can legitimately fail
 * (the vehicle still has fill/odometer/service entries, FK-restricted
 * server-side), and an optimistic local delete would make the vehicle
 * vanish from every picker while the backend still has it and its history.
 * Mirrors [FuelRepository]'s existing direct-REST pattern for filling
 * stations. Network failures (no connectivity) and HTTP errors (404/403/409)
 * on the create/update/delete calls themselves propagate as-is —
 * [ch.mcfx.urs.vehicle.VehicleViewModel] maps them to a user-facing message,
 * same [java.io.IOException]/[retrofit2.HttpException] split already used by
 * [ch.mcfx.urs.settings.ChangePasswordViewModel]. [refreshFromBackend] itself
 * is best-effort like every other repository's (standardized as part of
 * GitHub issue #53) — a failure there just means the local cache stays
 * stale until the next successful pull, it no longer fails the create/update
 * call that triggered it.
 */
class VehicleRepository(
    private val api: UrsApi,
    private val vehicleDao: VehicleDao,
) {
    fun observeVehicles(): Flow<List<VehicleEntity>> = vehicleDao.observeAll()

    suspend fun getVehicleById(id: String): VehicleEntity? = vehicleDao.getById(id)

    suspend fun createVehicle(payload: VehiclePayload) {
        api.createVehicle(payload)
        refreshFromBackend()
    }

    // The backend's PUT returns 204 No Content, so the updated row (with
    // its joined fuel_name) is re-fetched via refreshFromBackend() rather
    // than hand-built from the payload alone.
    suspend fun updateVehicle(id: String, payload: VehiclePayload) {
        api.updateVehicle(id, payload)
        refreshFromBackend()
    }

    suspend fun deleteVehicle(id: String) {
        api.deleteVehicle(id)
        vehicleDao.deleteById(id)
    }

    /** @return `true` if the refresh completed cleanly (see [PullCoordinator]). */
    suspend fun refreshFromBackend(): Boolean {
        try {
            api.getVehicles().forEach { vehicleDao.upsert(it.toEntity()) }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort only — see this repository's doc comment.
            android.util.Log.w("VehicleRepository", "refreshFromBackend failed", e)
            return false
        }
    }
}

fun VehicleDto.toEntity() = VehicleEntity(
    id = id,
    fuelId = fuelId,
    fuelName = fuelName,
    brand = brand,
    model = model,
    year = year,
    engineCode = engineCode.ifBlank { null },
    vehicleType = vehicleTypeFromRaw(vehicleType),
    color = color.ifBlank { null },
    vin = vin.ifBlank { null },
    registrationNumber = registrationNumber.ifBlank { null },
    typeApprovalNumber = typeApprovalNumber.ifBlank { null },
    displacementCcm = displacementCcm.ifBlank { null },
    powerKw = powerKw.ifBlank { null },
    powerPs = powerPs.ifBlank { null },
    weightKg = weightKg.ifBlank { null },
    firstRegistrationDate = firstRegistrationDate.ifBlank { null },
    lastMfkDate = lastMfkDate.ifBlank { null },
)
