package ch.mcfx.urs.journal

// Journal (GitHub issue #83) — generalizes Chores into a calendar-first
// activity tracker organized into user-defined domains. Runs alongside
// ChoresRoutes/ChoresScreen unchanged during the transition; not a rename.
object JournalRoutes {
    const val MONTH = "journal/month"
    const val DAY = "journal/day/{date}"
    const val OVERVIEW = "journal/overview"

    fun day(date: String): String = "journal/day/$date"
}
