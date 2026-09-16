package ch.mcfx.urs.settings

/**
 * One parsed line from `CHANGELOG.md`. Deliberately not a general-purpose
 * Markdown model — only the subset this app's own changelog actually uses
 * (confirmed by reading the file directly, not guessed): `#`/`##`/`###`
 * headers, flat `- ` bullets, no nested lists, no tables, no images. A line
 * using anything outside that set falls through to [ChangelogBlock.Paragraph]
 * and renders as plain text rather than being dropped.
 */
sealed interface ChangelogBlock {
    data class Header(val level: Int, val text: String) : ChangelogBlock
    data class Bullet(val text: String) : ChangelogBlock
    data class Paragraph(val text: String) : ChangelogBlock
}

object ChangelogParser {
    // Inline spans this file occasionally uses inside a header/bullet/
    // paragraph line — stripped down to their plain display text rather than
    // given distinct visual styling, since UrsText renders a single flat
    // TextStyle per line with no per-span formatting support.
    private val InlineCodeSpan = Regex("`([^`]*)`")
    private val InlineLink = Regex("""\[([^]]*)]\([^)]*\)""")

    fun parse(markdown: String): List<ChangelogBlock> = markdown.lineSequence().mapNotNull { rawLine ->
        val line = rawLine.trimEnd()
        when {
            line.isBlank() -> null
            line.startsWith("### ") -> ChangelogBlock.Header(3, renderInline(line.removePrefix("### ")))
            line.startsWith("## ") -> ChangelogBlock.Header(2, renderInline(line.removePrefix("## ")))
            line.startsWith("# ") -> ChangelogBlock.Header(1, renderInline(line.removePrefix("# ")))
            line.startsWith("- ") -> ChangelogBlock.Bullet(renderInline(line.removePrefix("- ")))
            else -> ChangelogBlock.Paragraph(renderInline(line))
        }
    }.toList()

    private fun renderInline(text: String): String = text.replace(InlineLink, "$1").replace(InlineCodeSpan, "$1")
}
