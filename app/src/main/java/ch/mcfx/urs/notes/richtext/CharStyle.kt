package ch.mcfx.urs.notes.richtext

/** Combinable inline character formatting — the three styles the toolbar can toggle. */
data class CharStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) {
    val isPlain: Boolean
        get() = !bold && !italic && !underline
}

/** Paragraph-level list kind a line can be part of. */
enum class ListType { BULLET, NUMBER }
