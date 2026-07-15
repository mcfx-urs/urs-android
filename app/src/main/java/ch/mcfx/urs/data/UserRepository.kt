package ch.mcfx.urs.data

import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.remote.UserDefaultDailyTargetHoursPayload
import kotlinx.serialization.SerializationException

class UserRepository(private val api: UrsApi) {

    suspend fun getDefaultDailyTargetHours(): String =
        try {
            api.getUsers().firstOrNull { it.id == UserDefaults.DEFAULT_USER_ID }?.defaultDailyTargetHours.orEmpty()
        } catch (_: SerializationException) {
            // The backend encodes an empty result set as JSON `null` instead of `[]`.
            ""
        }

    suspend fun setDefaultDailyTargetHours(hours: String) {
        api.updateUserDefaultDailyTargetHours(
            UserDefaults.DEFAULT_USER_ID,
            UserDefaultDailyTargetHoursPayload(defaultDailyTargetHours = hours),
        )
    }
}
