package ch.mcfx.urs.notes.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Low-level [AnnotatedString] plumbing shared by the markup parser/serializer
 * ([RichTextMarkup]) and the live editing operations ([RichTextEditing]).
 *
 * Two kinds of metadata ride along on the [AnnotatedString] itself rather
 * than a separate side model, via its built-in string-annotation mechanism:
 * - [ListAnnotationTag] marks a line's marker-prefix range ("• " or "2. ",
 *   including its leading indent spaces) with "$type:$level" — this is what
 *   lets a plain line that merely *looks* like "- foo" or "1. foo" (typed by
 *   the user, not produced by the list toolbar) round-trip as plain text
 *   instead of being misread as a real list item (see [RichTextMarkup]).
 * - [LinkAnnotationTag] marks a link's visible text range with its URL.
 *
 * Link *visuals* (accent color + underline) are deliberately kept out of the
 * canonical model entirely — see [withLinkVisuals] — so that resolving
 * manual bold/italic/underline via [resolveStyleRuns] never mistakes a
 * link's fixed display underline for a manually toggled one.
 */
internal const val ListAnnotationTag = "urs_list"
internal const val LinkAnnotationTag = "urs_link"
internal const val IndentSpacesPerLevel = 2
internal const val MaxListLevel = 4
internal const val BulletGlyph = "• "

internal data class ListMarker(val type: ListType, val level: Int, val markerRange: TextRange)

internal data class LinkRange(val range: TextRange, val url: String)

internal data class StyleRun(val range: TextRange, val style: CharStyle)

internal fun CharStyle.toSpanStyle(): SpanStyle = SpanStyle(
    fontWeight = if (bold) FontWeight.Bold else null,
    fontStyle = if (italic) FontStyle.Italic else null,
    textDecoration = if (underline) TextDecoration.Underline else null,
)

/** The list marker (if any) starting exactly at [lineStart] — the sole source of truth for "is this a real list line". */
internal fun AnnotatedString.listMarkerAt(lineStart: Int): ListMarker? {
    val annotation = getStringAnnotations(ListAnnotationTag, lineStart, lineStart)
        .firstOrNull { it.start == lineStart }
        ?: return null
    val (typeName, levelText) = annotation.item.split(":")
    return ListMarker(ListType.valueOf(typeName), levelText.toInt(), TextRange(annotation.start, annotation.end))
}

/** The link (if any) that strictly contains [offset] — used for tap-to-edit detection. */
internal fun AnnotatedString.linkAt(offset: Int): LinkRange? =
    getStringAnnotations(LinkAnnotationTag, offset, offset)
        .firstOrNull { it.start <= offset && it.end > offset }
        ?.let { LinkRange(TextRange(it.start, it.end), it.item) }

/** All links whose range intersects [range], clipped to it. */
internal fun AnnotatedString.linksIn(range: TextRange): List<LinkRange> =
    getStringAnnotations(LinkAnnotationTag, range.start, range.end)
        .map { LinkRange(TextRange(it.start.coerceAtLeast(range.start), it.end.coerceAtMost(range.end)), it.item) }
        .sortedBy { it.range.start }

/**
 * Folds all overlapping [AnnotatedString.spanStyles] within [target] into the minimal
 * set of non-overlapping [StyleRun]s. The single source of truth for "what
 * bold/italic/underline combination applies at a given position" — used both
 * by markup serialization and by the toolbar's selection-toggle logic.
 */
internal fun AnnotatedString.resolveStyleRuns(target: TextRange = TextRange(0, length)): List<StyleRun> {
    val start = target.start.coerceIn(0, length)
    val end = target.end.coerceIn(0, length)
    if (start >= end) return emptyList()
    val cuts = sortedSetOf(start, end)
    for (span in spanStyles) {
        val s = span.start.coerceIn(start, end)
        val e = span.end.coerceIn(start, end)
        if (s < e) {
            cuts += s
            cuts += e
        }
    }
    val sortedCuts = cuts.sorted()
    val runs = mutableListOf<StyleRun>()
    for (i in 0 until sortedCuts.size - 1) {
        val s = sortedCuts[i]
        val e = sortedCuts[i + 1]
        if (s >= e) continue
        var bold = false
        var italic = false
        var underline = false
        for (span in spanStyles) {
            if (span.start <= s && span.end >= e) {
                if (span.item.fontWeight == FontWeight.Bold) bold = true
                if (span.item.fontStyle == FontStyle.Italic) italic = true
                if (span.item.textDecoration?.contains(TextDecoration.Underline) == true) underline = true
            }
        }
        runs += StyleRun(TextRange(s, e), CharStyle(bold, italic, underline))
    }
    return runs
}

/** True only when [target] is non-collapsed and every character in it already has [flag] set. */
internal fun AnnotatedString.styleFlag(target: TextRange, flag: (CharStyle) -> Boolean): Boolean {
    if (target.collapsed) return false
    val runs = resolveStyleRuns(target)
    return runs.isNotEmpty() && runs.all { flag(it.style) }
}

/**
 * Rewrites the character-style layer for the whole string, applying
 * [transform] only to runs overlapping [target]. Rebuilds the layer from
 * scratch every time (rather than surgically patching individual spans) so
 * the "minimal non-overlapping partition" invariant [resolveStyleRuns]
 * relies on always holds afterward. List/link annotations are copied
 * through unchanged since their ranges are unaffected (the text itself
 * never changes length here).
 */
internal fun AnnotatedString.withCharStyle(target: TextRange, transform: (CharStyle) -> CharStyle): AnnotatedString {
    if (length == 0) return this
    val start = target.start.coerceIn(0, length)
    val end = target.end.coerceIn(0, length)
    val cuts = sortedSetOf(0, length, start, end)
    for (span in spanStyles) {
        cuts += span.start.coerceIn(0, length)
        cuts += span.end.coerceIn(0, length)
    }
    val sortedCuts = cuts.sorted()
    val builder = AnnotatedString.Builder(text)
    for (i in 0 until sortedCuts.size - 1) {
        val s = sortedCuts[i]
        val e = sortedCuts[i + 1]
        if (s >= e) continue
        var style = CharStyle()
        for (span in spanStyles) {
            if (span.start <= s && span.end >= e) {
                style = style.copy(
                    bold = style.bold || span.item.fontWeight == FontWeight.Bold,
                    italic = style.italic || span.item.fontStyle == FontStyle.Italic,
                    underline = style.underline || span.item.textDecoration?.contains(TextDecoration.Underline) == true,
                )
            }
        }
        if (s < end && e > start) style = transform(style)
        if (!style.isPlain) builder.addStyle(style.toSpanStyle(), s, e)
    }
    builder.copyAnnotationsFrom(this)
    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.copyAnnotationsFrom(source: AnnotatedString) {
    for (annotation in source.getStringAnnotations(ListAnnotationTag, 0, source.length)) {
        addStringAnnotation(ListAnnotationTag, annotation.item, annotation.start, annotation.end)
    }
    for (annotation in source.getStringAnnotations(LinkAnnotationTag, 0, source.length)) {
        addStringAnnotation(LinkAnnotationTag, annotation.item, annotation.start, annotation.end)
    }
}

/**
 * Display-only overlay adding the fixed link color/underline on top of the
 * canonical model, computed fresh on every render rather than stored — see
 * this file's top-level doc comment for why link visuals must stay out of
 * the persisted spanStyle layer.
 */
internal fun AnnotatedString.withLinkVisuals(accent: Color): AnnotatedString {
    val links = getStringAnnotations(LinkAnnotationTag, 0, length)
    if (links.isEmpty()) return this
    return AnnotatedString.Builder(this).apply {
        for (link in links) addStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline), link.start, link.end)
    }.toAnnotatedString()
}

/** Replaces [range] with a fresh link segment carrying [text]/[url], dropping whatever was there before. */
internal fun AnnotatedString.replaceRangeWithLink(range: TextRange, text: String, url: String): AnnotatedString {
    val linkPart = AnnotatedString.Builder(text).apply {
        addStringAnnotation(LinkAnnotationTag, url, 0, text.length)
    }.toAnnotatedString()
    return subSequence(0, range.start) + linkPart + subSequence(range.end, length)
}

/** Strips just the link annotation covering [range], keeping the characters and any other styling. */
internal fun AnnotatedString.withoutLink(range: TextRange): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    for (span in spanStyles) builder.addStyle(span.item, span.start, span.end)
    for (annotation in getStringAnnotations(ListAnnotationTag, 0, length)) {
        builder.addStringAnnotation(ListAnnotationTag, annotation.item, annotation.start, annotation.end)
    }
    for (annotation in getStringAnnotations(LinkAnnotationTag, 0, length)) {
        if (annotation.start >= range.end || annotation.end <= range.start) {
            builder.addStringAnnotation(LinkAnnotationTag, annotation.item, annotation.start, annotation.end)
        }
    }
    return builder.toAnnotatedString()
}

/** Start offset of the line containing [pos] (the character right after the previous '\n', or 0). */
internal fun lineStart(text: String, pos: Int): Int {
    if (pos <= 0) return 0
    val idx = text.lastIndexOf('\n', pos - 1)
    return if (idx == -1) 0 else idx + 1
}

/** Reads the literal digits out of an already-rendered numbered marker, e.g. "  3. " -> 3. */
internal fun currentMarkerNumber(text: String, marker: ListMarker): Int {
    val markerText = text.substring(marker.markerRange.start, marker.markerRange.end)
    return Regex("(\\d+)\\.").find(markerText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
}
