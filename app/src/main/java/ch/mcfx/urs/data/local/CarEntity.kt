package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Read-cache mirror of [ch.mcfx.urs.data.remote.CarDto]. Without this, the
 * Add-fill form's car picker is empty on a cold app start with no
 * connectivity — cars are otherwise never created from this app, so there's
 * no outbox/pending-write concern here, purely a refresh-on-reachable cache.
 */
@Entity(tableName = "car")
data class CarEntity(
    @PrimaryKey val id: String,
    val fuelId: String,
    val fuelName: String,
    val brand: String,
    val model: String,
    val year: String,
)
