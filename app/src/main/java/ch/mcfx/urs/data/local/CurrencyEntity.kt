package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local cache of `GET /api/v1/currency`, so the currency picker works fully offline. */
@Entity(tableName = "currency")
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val name: String,
)
