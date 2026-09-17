package ch.mcfx.urs.journal

/**
 * Domain colour/icon palette (GitHub issue #83) — deliberately separate from
 * [ch.mcfx.urs.chores.choreColorPalette]/[ch.mcfx.urs.chores.choreMaterialIcons]
 * so a domain and the types inside it never look like the same "kind" of
 * picker by coincidence (same reasoning as the `urs-web` port). A domain's
 * icon is always a plain emoji — no Material-icon tab, unlike a type's own
 * icon picker — domains are broad categories, not worth the richer picker.
 */
val journalDomainColorPalette: List<String> = listOf(
    "#8D6E63", "#5C6BC0", "#26A69A", "#EF6C00", "#7E57C2", "#66BB6A", "#EC407A", "#78909C",
)

val journalDomainIconChoices: List<String> = listOf("🏠", "❤️", "🚗", "💼", "📚", "⚽", "🎵", "✈️")

val defaultJournalDomainColor: String = journalDomainColorPalette.first()
val defaultJournalDomainIcon: String = journalDomainIconChoices.first()
