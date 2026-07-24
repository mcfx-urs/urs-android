package ch.mcfx.urs.data

import ch.mcfx.urs.auth.AuthTokenStore
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.remote.UserDefaultDailyTargetHoursPayload
import ch.mcfx.urs.data.remote.UserDto
import ch.mcfx.urs.data.remote.UserEmploymentPercentPayload
import ch.mcfx.urs.data.remote.UserHourlyWagePayload
import kotlinx.serialization.SerializationException

data class WorkSettings(
    val defaultDailyTargetHours: String,
    val employmentPercent: String,
    val hourlyWage: String,
)

// The work-time endpoints below still take an explicit user_id path segment
// on the backend (per-user scoping covered vehicles/fuel_fill/odometer/
// inventory_category/inventory_product/beer_log — work-time wasn't in that
// list, it already had its own user_id column from an earlier change). The
// id itself now comes from the logged-in session (AuthTokenStore.currentUserId,
// decoded from the access token) instead of the old hardcoded
// UserDefaults.DEFAULT_USER_ID placeholder.
class UserRepository(
    private val api: UrsApi,
    private val tokenStore: AuthTokenStore,
) {

    // Household member picker for UrsShareSheet — every other user
    // this account could share an inventory/list with. Same
    // empty-list-as-null backend quirk as everywhere else in this app.
    suspend fun getAllUsers(): List<UserDto> =
        try {
            api.getUsers()
        } catch (_: SerializationException) {
            emptyList()
        }

    suspend fun getWorkSettings(): WorkSettings {
        val userId = tokenStore.currentUserId
        val user = try {
            api.getUsers().firstOrNull { it.id == userId }
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
        val userId = tokenStore.currentUserId ?: return
        api.updateUserDefaultDailyTargetHours(userId, UserDefaultDailyTargetHoursPayload(defaultDailyTargetHours = hours))
    }

    suspend fun setEmploymentPercent(percent: String) {
        val userId = tokenStore.currentUserId ?: return
        api.updateUserEmploymentPercent(userId, UserEmploymentPercentPayload(employmentPercent = percent))
    }

    suspend fun setHourlyWage(wage: String) {
        val userId = tokenStore.currentUserId ?: return
        api.updateUserHourlyWage(userId, UserHourlyWagePayload(hourlyWage = wage))
    }

    // Super-user only — the backend itself rejects this with
    // 403 for anyone else, this is just the call site.
    suspend fun restartServer() = api.restartServer()

    // Throws if unreachable, returns normally once the backend responds —
    // used to poll for the server coming back up after restartServer().
    suspend fun ping() = api.ping()
}
