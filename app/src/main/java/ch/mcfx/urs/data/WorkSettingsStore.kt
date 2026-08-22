package ch.mcfx.urs.data

import android.content.Context

private const val PREFS_NAME = "work_settings_cache"
private const val KEY_TARGET_HOURS = "default_daily_target_hours"
private const val KEY_EMPLOYMENT_PERCENT = "employment_percent"
private const val KEY_HOURLY_WAGE = "hourly_wage"
private const val KEY_VACATION_PAY = "vacation_pay_surcharge_percent"
private const val KEY_HOLIDAY = "holiday_surcharge_percent"
private const val KEY_THIRTEENTH_MONTH = "thirteenth_month_surcharge_percent"
private const val KEY_AHV_IV_EO = "ahv_iv_eo_deduction_percent"
private const val KEY_ALV = "alv_deduction_percent"
private const val KEY_SUVA_NBU = "suva_nbu_deduction_percent"
private const val KEY_KTG = "ktg_deduction_percent"
private const val KEY_BVG = "bvg_deduction_amount"
private const val KEY_HAS_VALUE = "has_value"

/**
 * Last-known-value cache for [WorkSettings] — without this,
 * [ch.mcfx.urs.worktime.WorkTimeViewModel] starts every cold launch with no
 * settings at all until [UserRepository.getWorkSettings]'s network
 * round-trip resolves, so the monthly wage summary briefly computes from
 * empty target hours / 0% deduction rates instead of the user's real
 * numbers. Same reasoning as WorkTimeRepository's Room-backed
 * entries/overrides ("so the history screen has something to show on a cold
 * start"), just via plain SharedPreferences (same pattern as
 * [ch.mcfx.urs.settings.ThemeSettingsStore]) rather than a Room table —
 * this is a handful of per-user strings, not a growing local dataset.
 */
class WorkSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): WorkSettings? {
        if (!prefs.getBoolean(KEY_HAS_VALUE, false)) return null
        return WorkSettings(
            defaultDailyTargetHours = prefs.getString(KEY_TARGET_HOURS, "").orEmpty(),
            employmentPercent = prefs.getString(KEY_EMPLOYMENT_PERCENT, "").orEmpty(),
            hourlyWage = prefs.getString(KEY_HOURLY_WAGE, "").orEmpty(),
            wageRules = WageRules(
                vacationPaySurchargePercent = prefs.getString(KEY_VACATION_PAY, ""),
                holidaySurchargePercent = prefs.getString(KEY_HOLIDAY, ""),
                thirteenthMonthSurchargePercent = prefs.getString(KEY_THIRTEENTH_MONTH, ""),
                ahvIvEoDeductionPercent = prefs.getString(KEY_AHV_IV_EO, ""),
                alvDeductionPercent = prefs.getString(KEY_ALV, ""),
                suvaNbuDeductionPercent = prefs.getString(KEY_SUVA_NBU, ""),
                ktgDeductionPercent = prefs.getString(KEY_KTG, ""),
                bvgDeductionAmount = prefs.getString(KEY_BVG, ""),
            ),
        )
    }

    /** Called from [ch.mcfx.urs.auth.AuthRepository.logout] — see its own doc comment on why every per-user local cache needs this. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    fun write(settings: WorkSettings) {
        prefs.edit()
            .putBoolean(KEY_HAS_VALUE, true)
            .putString(KEY_TARGET_HOURS, settings.defaultDailyTargetHours)
            .putString(KEY_EMPLOYMENT_PERCENT, settings.employmentPercent)
            .putString(KEY_HOURLY_WAGE, settings.hourlyWage)
            .putString(KEY_VACATION_PAY, settings.wageRules.vacationPaySurchargePercent)
            .putString(KEY_HOLIDAY, settings.wageRules.holidaySurchargePercent)
            .putString(KEY_THIRTEENTH_MONTH, settings.wageRules.thirteenthMonthSurchargePercent)
            .putString(KEY_AHV_IV_EO, settings.wageRules.ahvIvEoDeductionPercent)
            .putString(KEY_ALV, settings.wageRules.alvDeductionPercent)
            .putString(KEY_SUVA_NBU, settings.wageRules.suvaNbuDeductionPercent)
            .putString(KEY_KTG, settings.wageRules.ktgDeductionPercent)
            .putString(KEY_BVG, settings.wageRules.bvgDeductionAmount)
            .apply()
    }
}
