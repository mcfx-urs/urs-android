package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import ch.mcfx.urs.data.VehicleType

/**
 * Read-cache mirror of [ch.mcfx.urs.data.remote.VehicleDto]. Vehicle
 * create/edit/delete now goes through [ch.mcfx.urs.data.VehicleRepository]
 * (direct REST, no outbox) — this cache exists purely so pickers (Add-fill
 * form, Service form) still have something to show on a cold start with no
 * connectivity, kept warm by [ch.mcfx.urs.data.FuelRepository.refreshFromBackend].
 */
@Entity(tableName = "vehicle")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val fuelId: String,
    val fuelName: String,
    val brand: String,
    val model: String,
    val year: String,
    val engineCode: String? = null,
    val vehicleType: VehicleType = VehicleType.CAR,
    val color: String? = null,
    val vin: String? = null,
    val registrationNumber: String? = null,
    val typeApprovalNumber: String? = null,
    val displacementCcm: String? = null,
    val powerKw: String? = null,
    val powerPs: String? = null,
    val weightKg: String? = null,
    val firstRegistrationDate: String? = null,
    val lastMfkDate: String? = null,
    // A pseudo-vehicle representing a portable fuel container (e.g. a
    // jerry can) — no odometer/consumption meaning, can be named as a
    // transfer fill's sourceVehicleId (see mcfx-urs/urs-android#87).
    val isContainer: Boolean = false,
)
