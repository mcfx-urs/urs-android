package ch.mcfx.urs.notes

import ch.mcfx.urs.notes.richtext.LinkAnnotationTag
import ch.mcfx.urs.notes.richtext.RichTextMarkup

/**
 * Flattens a note's stored rich-text markup into plain text for export
 * (the share sheet's body, a calendar event's description).
 *
 * Character-level styling (bold/italic/underline) is dropped entirely with
 * no delimiters emitted — no single messenger's markup syntax renders
 * correctly across all the targets a shared note might land in, so any
 * emitted markers would just look like stray punctuation somewhere.
 *
 * List lines keep the prefix [RichTextMarkup.parse] already renders as real
 * leading characters: `"• "` for bullets, `"1. "`/`"2. "` for numbered
 * items, two leading spaces per nesting level. Links become `text (url)`,
 * or just the bare URL when the visible text already is the URL (avoids
 * printing an auto-linked URL twice).
 */
fun noteMarkupToPlainText(markup: String): String {
    if (markup.isEmpty()) return ""
    val annotated = RichTextMarkup.parse(markup)
    val text = annotated.text
    val links = annotated.getStringAnnotations(LinkAnnotationTag, 0, text.length).sortedBy { it.start }
    if (links.isEmpty()) return text
    return buildString {
        var cursor = 0
        for (link in links) {
            if (link.start < cursor) continue
            append(text, cursor, link.start)
            val visible = text.substring(link.start, link.end)
            append(if (visible == link.item) link.item else "$visible (${link.item})")
            cursor = link.end
        }
        append(text, cursor, text.length)
    }
}
