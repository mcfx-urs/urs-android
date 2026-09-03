package ch.mcfx.urs.data

import ch.mcfx.urs.auth.AuthTokenStore
import ch.mcfx.urs.data.remote.LogLevelDto
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.remote.UserDefaultDailyTargetHoursPayload
import ch.mcfx.urs.data.remote.UserDto
import ch.mcfx.urs.data.remote.UserDefaultVehiclePayload
import ch.mcfx.urs.data.remote.UserEmploymentPercentPayload
import ch.mcfx.urs.data.remote.UserHourlyWagePayload
import ch.mcfx.urs.data.remote.UserWageRulesPayload
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException

data class WorkSettings(
    val defaultDailyTargetHours: String,
    val employmentPercent: String,
    val hourlyWage: String,
    val wageRules: WageRules,
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
    private val defaultVehicleStore: DefaultVehicleStore,
) {

    /** Last-known server-stored default vehicle id; null when none is set. Observed by the fuel/home surfaces. */
    val defaultVehicleId: StateFlow<String?> = defaultVehicleStore.defaultVehicleId

    /** Pulls the server's current default vehicle into the local cache. Lets network errors propagate; callers guard. */
    suspend fun refreshDefaultVehicleId() {
        val userId = tokenStore.currentUserId ?: return
        val serverValue = try {
            api.getUsers().firstOrNull { it.id == userId }?.defaultVehicleId
        } catch (_: SerializationException) {
            return
        }
        defaultVehicleStore.set(serverValue)
    }

    /** Updates the cache immediately (optimistic) then persists server-side. Pass null/"" to clear. */
    suspend fun setDefaultVehicleId(vehicleId: String?) {
        defaultVehicleStore.set(vehicleId)
        api.updateUserDefaultVehicle(UserDefaultVehiclePayload(defaultVehicleId = vehicleId.orEmpty()))
    }

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
            wageRules = WageRules(
                vacationPaySurchargePercent = user?.vacationPaySurchargePercent.orEmpty(),
                holidaySurchargePercent = user?.holidaySurchargePercent.orEmpty(),
                thirteenthMonthSurchargePercent = user?.thirteenthMonthSurchargePercent.orEmpty(),
                ahvIvEoDeductionPercent = user?.ahvIvEoDeductionPercent.orEmpty(),
                alvDeductionPercent = user?.alvDeductionPercent.orEmpty(),
                suvaNbuDeductionPercent = user?.suvaNbuDeductionPercent.orEmpty(),
                ktgDeductionPercent = user?.ktgDeductionPercent.orEmpty(),
            ).withDefaults(),
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

    suspend fun setWageRules(rules: WageRules) {
        val userId = tokenStore.currentUserId ?: return
        api.updateUserWageRules(
            userId,
            UserWageRulesPayload(
                vacationPaySurchargePercent = rules.vacationPaySurchargePercent.orEmpty(),
                holidaySurchargePercent = rules.holidaySurchargePercent.orEmpty(),
                thirteenthMonthSurchargePercent = rules.thirteenthMonthSurchargePercent.orEmpty(),
                ahvIvEoDeductionPercent = rules.ahvIvEoDeductionPercent.orEmpty(),
                alvDeductionPercent = rules.alvDeductionPercent.orEmpty(),
                suvaNbuDeductionPercent = rules.suvaNbuDeductionPercent.orEmpty(),
                ktgDeductionPercent = rules.ktgDeductionPercent.orEmpty(),
            ),
        )
    }

    // Super-user only — the backend itself rejects this with
    // 403 for anyone else, this is just the call site.
    suspend fun restartServer() = api.restartServer()

    // Throws if unreachable, returns normally once the backend responds —
    // used to poll for the server coming back up after restartServer().
    suspend fun ping() = api.ping()

    // Super-user only — reads/retunes the backend's runtime log level.
    suspend fun getLogLevel() = api.getLogLevel().level

    suspend fun setLogLevel(level: String) = api.setLogLevel(LogLevelDto(level))
}
