package ch.mcfx.urs.data

import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.remote.UserDefaultDailyTargetHoursPayload
import ch.mcfx.urs.data.remote.UserEmploymentPercentPayload
import ch.mcfx.urs.data.remote.UserHourlyWagePayload
import kotlinx.serialization.SerializationException

data class WorkSettings(
    val defaultDailyTargetHours: String,
    val employmentPercent: String,
    val hourlyWage: String,
)

class UserRepository(private val api: UrsApi) {

    suspend fun getWorkSettings(): WorkSettings {
        val user = try {
            api.getUsers().firstOrNull { it.id == UserDefaults.DEFAULT_USER_ID }
        } catch (_: SerializationException) {
            // The backend encodes an empty result set as JSON `null` instead of `[]`.
            null
        }
        return WorkSettings(
            defaultDailyTargetHours = user?.defaultDailyTargetHours.orEmpty(),
            employmentPercent = user?.employmentPercent.orEmpty(),
            hourlyWage = user?.hourlyWage.orEmpty(),
        )
    }

    suspend fun setDefaultDailyTargetHours(hours: String) {
        api.updateUserDefaultDailyTargetHours(
            UserDefaults.DEFAULT_USER_ID,
            UserDefaultDailyTargetHoursPayload(defaultDailyTargetHours = hours),
        )
    }

    suspend fun setEmploymentPercent(percent: String) {
        api.updateUserEmploymentPercent(
            UserDefaults.DEFAULT_USER_ID,
            UserEmploymentPercentPayload(employmentPercent = percent),
        )
    }

    suspend fun setHourlyWage(wage: String) {
        api.updateUserHourlyWage(
            UserDefaults.DEFAULT_USER_ID,
            UserHourlyWagePayload(hourlyWage = wage),
        )
    }
}
