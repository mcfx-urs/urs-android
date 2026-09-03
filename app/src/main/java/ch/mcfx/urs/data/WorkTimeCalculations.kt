package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.WorkTimeEntryWithBreaks
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun rangeHours(start: String, end: String): Float? {
    val t1 = runCatching { LocalTime.parse(start, TimeFormatter) }.getOrNull() ?: return null
    val t2 = runCatching { LocalTime.parse(end, TimeFormatter) }.getOrNull() ?: return null
    return java.time.Duration.between(t1, t2).toMinutes() / 60f
}

private const val PaidBreakHours = 0.25f

// Primitive-parameter core so it can be shared between a saved entry
// (WorkTimeEntryWithBreaks below) and the add/edit form's live preview
// (WorkTimeFormState, in the worktime package) without either depending on
// the other's type.
fun dailyHoursWorked(workStart: String, workEnd: String, paidBreak: Boolean, breaks: List<Pair<String, String>>): Float? =
    rangeHours(workStart, workEnd)?.let { workSpan ->
        val withoutBreaks = breaks.fold(workSpan) { acc, (start, end) -> acc - (rangeHours(start, end) ?: 0f) }
        if (paidBreak) withoutBreaks + PaidBreakHours else withoutBreaks
    }

private fun WorkTimeEntryWithBreaks.dailyHoursWorked(): Float? =
    dailyHoursWorked(entry.workStart, entry.workEnd, entry.paidBreak, breaks.map { it.startTime to it.endTime })

/**
 * Client-side mirror of urs-backend's `computeDailyTotals` — kept so a
 * not-yet-synced entry can show its total immediately, without waiting for
 * a server round-trip. Both sides must be changed together if the formula
 * ever changes.
 */
data class WorkTimeTotals(val dailyTotalHours: Float?, val overUndertimeHours: Float?)

fun computeTotals(dailyTotalHours: Float?, targetDailyHours: String, userDefaultTargetHours: String?): WorkTimeTotals {
    dailyTotalHours ?: return WorkTimeTotals(null, null)
    val target = targetDailyHours.toFloatOrNull() ?: userDefaultTargetHours?.toFloatOrNull()
    return WorkTimeTotals(dailyTotalHours, target?.let { dailyTotalHours - it })
}

fun WorkTimeEntryWithBreaks.computeTotals(userDefaultTargetHours: String?): WorkTimeTotals =
    computeTotals(dailyHoursWorked(), entry.targetDailyHours, userDefaultTargetHours)

/** Number of Monday–Friday calendar days in a given month — the exact "how many days could theoretically be worked" count. */
fun possibleWeekdaysInMonth(year: Int, month: Int): Int {
    val yearMonth = YearMonth.of(year, month)
    return (1..yearMonth.lengthOfMonth()).count { day ->
        val dayOfWeek = yearMonth.atDay(day).dayOfWeek
        dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
    }
}

data class MonthlySummary(
    val actualHours: Float,
    val overUndertimeHours: Float?,
    /** Null whenever [WageRules.withDefaults]-independent inputs (e.g. hourly wage) aren't configured — see [computeWage]. */
    val wageBreakdown: WageBreakdown?,
    /** Null while [year]/[month] (see [computeMonthlySummary]) is the current, still-active month. */
    val percentOfContractSoll: Float?,
)

/**
 * The fixed, built-in set of wage-rule rates a user configures once
 * (Settings → Work Settings → Surcharges & deductions) — mirrors
 * urs-backend's `UserWageRules`. All percentages as plain strings (e.g.
 * `"10.6"`), same convention as every other user-editable numeric setting
 * in this app; a blank/unparseable rate contributes nothing (0), so an
 * unconfigured rule simply doesn't affect the total rather than blocking it.
 */
data class WageRules(
    val vacationPaySurchargePercent: String?,
    val holidaySurchargePercent: String?,
    val thirteenthMonthSurchargePercent: String?,
    val ahvIvEoDeductionPercent: String?,
    val alvDeductionPercent: String?,
    val suvaNbuDeductionPercent: String?,
    val ktgDeductionPercent: String?,
    val bvgDeductionAmount: String?,
)

// Pre-filled the first time a user's wage rules are loaded (backend value
// blank), so the feature works out of the box and only needs adjusting, not
// filling in from scratch — see GitHub issue #11's worked example. AHV/ALV
// match current official Swiss employee-share rates; BVG has no sensible
// default (varies per pension plan/coordinated salary), so it starts at 0.
val DefaultWageRules = WageRules(
    vacationPaySurchargePercent = "10.6",
    holidaySurchargePercent = "3.8",
    thirteenthMonthSurchargePercent = "8.33",
    ahvIvEoDeductionPercent = "5.3",
    alvDeductionPercent = "1.1",
    suvaNbuDeductionPercent = "1.76",
    ktgDeductionPercent = "1.621",
    bvgDeductionAmount = "0",
)

/** Substitutes the built-in default for any field the caller left blank/unset. */
fun WageRules.withDefaults(): WageRules = WageRules(
    vacationPaySurchargePercent = vacationPaySurchargePercent?.takeIf { it.isNotBlank() } ?: DefaultWageRules.vacationPaySurchargePercent,
    holidaySurchargePercent = holidaySurchargePercent?.takeIf { it.isNotBlank() } ?: DefaultWageRules.holidaySurchargePercent,
    thirteenthMonthSurchargePercent = thirteenthMonthSurchargePercent?.takeIf { it.isNotBlank() } ?: DefaultWageRules.thirteenthMonthSurchargePercent,
    ahvIvEoDeductionPercent = ahvIvEoDeductionPercent?.takeIf { it.isNotBlank() } ?: DefaultWageRules.ahvIvEoDeductionPercent,
    alvDeductionPercent = alvDeductionPercent?.takeIf { it.isNotBlank() } ?: DefaultWageRules.alvDeductionPercent,
    suvaNbuDeductionPercent = suvaNbuDeductionPercent?.takeIf { it.isNotBlank() } ?: DefaultWageRules.suvaNbuDeductionPercent,
    ktgDeductionPercent = ktgDeductionPercent?.takeIf { it.isNotBlank() } ?: DefaultWageRules.ktgDeductionPercent,
    bvgDeductionAmount = bvgDeductionAmount?.takeIf { it.isNotBlank() } ?: DefaultWageRules.bvgDeductionAmount,
)

/** One row of [WageBreakdown]'s surcharge/deduction chain — [percent] is null for BVG, the chain's only flat-amount item. */
enum class WageLineItemType { VACATION_PAY, HOLIDAY_PAY, THIRTEENTH_MONTH, AHV_IV_EO, ALV, SUVA_NBU, KTG, BVG }

data class WageLineItem(val type: WageLineItemType, val percent: Float?, val amount: Float)

/** CHF meal allowance owed per calendar day a work-time entry flags via [ch.mcfx.urs.data.local.WorkTimeEntryEntity.mealAllowance]. */
const val MealAllowancePerDay = 18f

data class WageBreakdown(
    val baseWage: Float,
    val surcharges: List<WageLineItem>,
    val gross: Float,
    val deductions: List<WageLineItem>,
    val net: Float,
    /** Distinct calendar days in the month with the meal-allowance flag set — see [computeMonthlySummary]'s dedup. */
    val mealAllowanceDays: Int,
    /** Part of [gross] but excluded from every deduction base, so it flows straight through to [net]. */
    val mealAllowanceAmount: Float,
)

private fun applyPercent(base: Float, percent: String?): Float = base * (percent?.toFloatOrNull() ?: 0f) / 100f

private fun Float.roundToNearestFiveRappen(): Float = Math.round(this * 20f) / 20f

/**
 * Chained surcharges (each a % of a running subtotal, order matters), then
 * the meal allowance ([mealAllowanceDays] × [MealAllowancePerDay]) added
 * into [WageBreakdown.gross], then chained deductions (each a % of the gross
 * *before* the meal allowance, plus BVG's fixed amount) — see the worked
 * example in GitHub issue #11 this mirrors. The meal allowance is part of
 * gross but carries no surcharge or deduction, so it passes straight through
 * to [WageBreakdown.net]. Every line is rounded to 5 Rappen and the totals
 * are the sum of the rounded lines, as on the official payslip.
 */
fun computeWage(baseWage: Float, rules: WageRules, mealAllowanceDays: Int = 0): WageBreakdown {
    fun surcharge(type: WageLineItemType, percent: String?, base: Float) =
        WageLineItem(type, percent?.toFloatOrNull() ?: 0f, applyPercent(base, percent).roundToNearestFiveRappen())

    val vacationPay = surcharge(WageLineItemType.VACATION_PAY, rules.vacationPaySurchargePercent, baseWage)
    val holidayPay = surcharge(WageLineItemType.HOLIDAY_PAY, rules.holidaySurchargePercent, baseWage)
    val beforeThirteenthMonth = baseWage + vacationPay.amount + holidayPay.amount
    val thirteenthMonth = surcharge(WageLineItemType.THIRTEENTH_MONTH, rules.thirteenthMonthSurchargePercent, beforeThirteenthMonth)
    val grossBeforeMealAllowance = beforeThirteenthMonth + thirteenthMonth.amount

    val mealAllowanceAmount = mealAllowanceDays * MealAllowancePerDay
    val gross = grossBeforeMealAllowance + mealAllowanceAmount

    fun deduction(type: WageLineItemType, percent: String?) =
        WageLineItem(type, percent?.toFloatOrNull() ?: 0f, applyPercent(grossBeforeMealAllowance, percent).roundToNearestFiveRappen())

    val ahv = deduction(WageLineItemType.AHV_IV_EO, rules.ahvIvEoDeductionPercent)
    val alv = deduction(WageLineItemType.ALV, rules.alvDeductionPercent)
    val suva = deduction(WageLineItemType.SUVA_NBU, rules.suvaNbuDeductionPercent)
    val ktg = deduction(WageLineItemType.KTG, rules.ktgDeductionPercent)
    val bvg = WageLineItem(WageLineItemType.BVG, percent = null, amount = (rules.bvgDeductionAmount?.toFloatOrNull() ?: 0f).roundToNearestFiveRappen())

    val totalDeductions = ahv.amount + alv.amount + suva.amount + ktg.amount + bvg.amount
    val net = (gross - totalDeductions).roundToNearestFiveRappen()

    return WageBreakdown(
        baseWage = baseWage,
        surcharges = listOf(vacationPay, holidayPay, thirteenthMonth),
        gross = gross,
        deductions = listOf(ahv, alv, suva, ktg, bvg),
        net = net,
        mealAllowanceDays = mealAllowanceDays,
        mealAllowanceAmount = mealAllowanceAmount,
    )
}

/**
 * Aggregates a calendar month's entries into two deliberately independent
 * measures:
 *
 * - **Plus/Minus** (`overUndertimeHours`) is self-referential: its Soll is
 *   `daysWorked * targetHoursPerDay`, where `daysWorked` is either the
 *   manual [overrideDaysWorked] or a raw count of entries that month.
 *   Answers "on the days I logged, did I hit my daily target?" — always
 *   meaningful, including for the currently active month, since it never
 *   expects hours for days that haven't happened yet (no "always behind
 *   mid-month" distortion).
 * - **percentOfContractSoll** answers a different question — "of my full
 *   month's contractual hour obligation, what fraction did I actually
 *   work?" — using the employment percentage against every weekday in the
 *   month (`possibleWeekdaysInMonth(year, month) * employmentPercent/100 *
 *   targetHoursPerDay`), entirely independent of daysWorked/the override.
 *   Only meaningful once a month has fully ended (its weekday count is
 *   otherwise the full month's, so it would always read as "behind" for
 *   the active month purely because the month isn't over yet) — callers
 *   pass [isCurrentMonth] and get `null` back for the active month.
 */
fun computeMonthlySummary(
    entries: List<WorkTimeEntryWithBreaks>,
    year: Int,
    month: Int,
    employmentPercent: String?,
    targetHoursPerDay: String?,
    hourlyWage: String?,
    wageRules: WageRules,
    overrideDaysWorked: String?,
    isCurrentMonth: Boolean,
): MonthlySummary {
    val monthPrefix = "%04d-%02d".format(year, month)
    val monthEntries = entries.filter { it.entry.date.startsWith(monthPrefix) }
    val actualHours = monthEntries.sumOf { (it.dailyHoursWorked() ?: 0f).toDouble() }.toFloat()

    val dailyTarget = targetHoursPerDay?.toFloatOrNull()
    val daysWorked = overrideDaysWorked?.toFloatOrNull() ?: monthEntries.size.toFloat()
    val plusMinusSoll = dailyTarget?.let { daysWorked * it }

    val percentOfContractSoll = if (isCurrentMonth) {
        null
    } else {
        val percent = employmentPercent?.toFloatOrNull()
        val contractSollHours = if (percent != null && dailyTarget != null) {
            possibleWeekdaysInMonth(year, month) * (percent / 100f) * dailyTarget
        } else {
            null
        }
        contractSollHours?.takeIf { it != 0f }?.let { actualHours / it * 100f }
    }

    // Capped at one flagged day per calendar date, not per entry — a split
    // shift logged as two entries the same day must not double the
    // allowance (GitHub issue #24).
    val mealAllowanceDays = monthEntries.filter { it.entry.mealAllowance }.map { it.entry.date }.distinct().size
    val wage = hourlyWage?.toFloatOrNull()?.let { computeWage(actualHours * it, wageRules, mealAllowanceDays) }

    return MonthlySummary(
        actualHours = actualHours,
        overUndertimeHours = plusMinusSoll?.let { actualHours - it },
        wageBreakdown = wage,
        percentOfContractSoll = percentOfContractSoll,
    )
}
