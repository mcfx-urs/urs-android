package ch.mcfx.urs.notes.richtext

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange

/**
 * Hand-rolled markup format for note content — not Markdown/CommonMark, just
 * the small closed syntax this editor itself reads and writes:
 *
 * - `**text**` bold, `~text~` italic, `__text__` underline, `[text](url)` link
 *   (italic deliberately doesn't share a character with bold's `**` or
 *   underline's `__` — a single `*`/`_` delimiter would merge with the
 *   adjacent double one into an ambiguous run wherever both styles cover
 *   the same text, e.g. `**` + `*text*` + `**` = `***text***`, which a
 *   greedy left-to-right parser can't split back apart correctly)
 * - a line starting with `- ` is a bullet item, `1. ` a numbered item
 *   (renumbered per level on save); 2 leading spaces per indent level (0-4)
 *
 * [parseRichText] turns a stored string back into the editable
 * [AnnotatedString] (list lines get their marker glyph rendered as real
 * leading characters, tagged via [ListAnnotationTag] so [serializeRichText]
 * can tell a genuine list line apart from plain text that merely starts
 * with a look-alike sequence — see [RichTextSpans]'s doc comment).
 * [serializeRichText] is the exact inverse.
 *
 * Literal `\ * _ [ ] ( )` characters that are plain content (not an actual
 * style/link boundary) are backslash-escaped on save and un-escaped on
 * load so the format round-trips unambiguously. A plain line that happens
 * to start with something list-marker-shaped is escaped the same way so it
 * doesn't get misread as a list item on the next load.
 */
object RichTextMarkup {
    fun parse(markup: String): AnnotatedString = parseRichText(markup)

    fun serialize(text: AnnotatedString): String = serializeRichText(text)
}

private val BulletLineRegex = Regex("^- (.*)$", RegexOption.DOT_MATCHES_ALL)
private val NumberedLineRegex = Regex("^(\\d+)\\. (.*)$", RegexOption.DOT_MATCHES_ALL)
private val NumberedLookalikeRegex = Regex("^\\d+\\. ")
private val EscapeChars = charArrayOf('\\', '*', '_', '~', '[', ']', '(', ')')

private fun parseRichText(markup: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = markup.split("\n")
    for ((index, rawLine) in lines.withIndex()) {
        if (index > 0) builder.append("\n")
        val lineStart = builder.length
        val indentCount = rawLine.takeWhile { it == ' ' }.length
        val level = (indentCount / IndentSpacesPerLevel).coerceIn(0, MaxListLevel)
        val afterIndent = rawLine.substring(indentCount)
        val bulletMatch = BulletLineRegex.find(afterIndent)
        val numberedMatch = if (bulletMatch == null) NumberedLineRegex.find(afterIndent) else null
        when {
            bulletMatch != null -> {
                builder.append(" ".repeat(level * IndentSpacesPerLevel))
                builder.append(BulletGlyph)
                val markerEnd = builder.length
                builder.addStringAnnotation(ListAnnotationTag, "${ListType.BULLET.name}:$level", lineStart, markerEnd)
                parseInline(builder, bulletMatch.groupValues[1])
            }
            numberedMatch != null -> {
                val number = numberedMatch.groupValues[1]
                builder.append(" ".repeat(level * IndentSpacesPerLevel))
                builder.append("$number. ")
                val markerEnd = builder.length
                builder.addStringAnnotation(ListAnnotationTag, "${ListType.NUMBER.name}:$level", lineStart, markerEnd)
                parseInline(builder, numberedMatch.groupValues[2])
            }
            else -> parseInline(builder, rawLine)
        }
    }
    return builder.toAnnotatedString()
}

private fun parseInline(builder: AnnotatedString.Builder, s: String, style: CharStyle = CharStyle()) {
    var i = 0
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            appendWithStyle(builder, plain.toString(), style)
            plain.clear()
        }
    }
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\\' && i + 1 < s.length -> {
                plain.append(s[i + 1])
                i += 2
            }
            s.startsWith("**", i) -> {
                val close = findClosing(s, i + 2, "**")
                if (close != -1) {
                    flush()
                    parseInline(builder, s.substring(i + 2, close), style.copy(bold = true))
                    i = close + 2
                } else {
                    plain.append(c)
                    i++
                }
            }
            s.startsWith("__", i) -> {
                val close = findClosing(s, i + 2, "__")
                if (close != -1) {
                    flush()
                    parseInline(builder, s.substring(i + 2, close), style.copy(underline = true))
                    i = close + 2
                } else {
                    plain.append(c)
                    i++
                }
            }
            c == '~' -> {
                val close = findClosing(s, i + 1, "~")
                if (close != -1) {
                    flush()
                    parseInline(builder, s.substring(i + 1, close), style.copy(italic = true))
                    i = close + 1
                } else {
                    plain.append(c)
                    i++
                }
            }
            c == '[' -> {
                val link = tryParseLink(s, i)
                if (link != null) {
                    flush()
                    val linkStart = builder.length
                    parseInline(builder, link.text, style)
                    builder.addStringAnnotation(LinkAnnotationTag, link.url, linkStart, builder.length)
                    i = link.nextIndex
                } else {
                    plain.append(c)
                    i++
                }
            }
            else -> {
                plain.append(c)
                i++
            }
        }
    }
    flush()
}

private fun appendWithStyle(builder: AnnotatedString.Builder, text: String, style: CharStyle) {
    val start = builder.length
    builder.append(text)
    if (!style.isPlain) builder.addStyle(style.toSpanStyle(), start, builder.length)
}

/** First unescaped occurrence of [delim] at or after [from], skipping backslash-escaped pairs. */
private fun findClosing(s: String, from: Int, delim: String): Int {
    var i = from
    while (i <= s.length - delim.length) {
        if (s[i] == '\\') {
            i += 2
            continue
        }
        if (s.startsWith(delim, i)) return i
        i++
    }
    return -1
}

private data class ParsedLink(val text: String, val url: String, val nextIndex: Int)

private fun tryParseLink(s: String, start: Int): ParsedLink? {
    val textClose = findClosing(s, start + 1, "]")
    if (textClose == -1 || textClose + 1 >= s.length || s[textClose + 1] != '(') return null
    val urlClose = findClosing(s, textClose + 2, ")")
    if (urlClose == -1) return null
    val text = s.substring(start + 1, textClose)
    val url = unescapeUrl(s.substring(textClose + 2, urlClose))
    return ParsedLink(text, url, urlClose + 1)
}

private fun escapeLiteral(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        if (c in EscapeChars) sb.append('\\')
        sb.append(c)
    }
    return sb.toString()
}

private fun escapeUrl(url: String): String = url.replace("\\", "\\\\").replace(")", "\\)")

private fun unescapeUrl(s: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length) {
            sb.append(s[i + 1])
            i += 2
        } else {
            sb.append(s[i])
            i++
        }
    }
    return sb.toString()
}

private fun looksLikeListMarker(content: String): Boolean =
    content.startsWith("- ") || NumberedLookalikeRegex.find(content) != null

private class ListNumbering(val level: Int, val type: ListType, var index: Int)

private fun serializeRichText(text: AnnotatedString): String {
    val lines = mutableListOf<String>()
    val stack = ArrayDeque<ListNumbering>()
    var lineStartOffset = 0
    val fullText = text.text
    while (true) {
        val newlineIndex = fullText.indexOf('\n', lineStartOffset)
        val lineEnd = if (newlineIndex == -1) fullText.length else newlineIndex
        lines += serializeLine(text, lineStartOffset, lineEnd, stack)
        if (newlineIndex == -1) break
        lineStartOffset = newlineIndex + 1
    }
    return lines.joinToString("\n")
}

private fun serializeLine(text: AnnotatedString, lineStart: Int, lineEnd: Int, stack: ArrayDeque<ListNumbering>): String {
    val marker = text.listMarkerAt(lineStart)
    if (marker == null) {
        stack.clear()
        val content = emitLineContent(text, lineStart, lineEnd)
        return if (looksLikeListMarker(content)) "\\$content" else content
    }

    while (stack.isNotEmpty() && stack.last().level > marker.level) stack.removeLast()
    val number: Int
    if (stack.isNotEmpty() && stack.last().level == marker.level) {
        val top = stack.last()
        if (top.type == marker.type) {
            top.index += 1
            number = top.index
        } else {
            stack.removeLast()
            stack.addLast(ListNumbering(marker.level, marker.type, 1))
            number = 1
        }
    } else {
        stack.addLast(ListNumbering(marker.level, marker.type, 1))
        number = 1
    }

    val indent = " ".repeat(marker.level * IndentSpacesPerLevel)
    val markerText = if (marker.type == ListType.BULLET) "- " else "$number. "
    val content = emitLineContent(text, marker.markerRange.end, lineEnd)
    return indent + markerText + content
}

private fun emitLineContent(text: AnnotatedString, start: Int, end: Int): String {
    if (start >= end) return ""
    val links = text.linksIn(TextRange(start, end))
    val sb = StringBuilder()
    var cursor = start
    for (link in links) {
        sb.append(emitInline(text, cursor, link.range.start))
        sb.append('[').append(emitInline(text, link.range.start, link.range.end)).append("](")
            .append(escapeUrl(link.url)).append(')')
        cursor = link.range.end
    }
    sb.append(emitInline(text, cursor, end))
    return sb.toString()
}

private fun emitInline(text: AnnotatedString, start: Int, end: Int): String {
    if (start >= end) return ""
    val sb = StringBuilder()
    for (run in text.resolveStyleRuns(TextRange(start, end))) {
        var wrapped = escapeLiteral(text.text.substring(run.range.start, run.range.end))
        if (run.style.underline) wrapped = "__${wrapped}__"
        if (run.style.italic) wrapped = "~${wrapped}~"
        if (run.style.bold) wrapped = "**${wrapped}**"
        sb.append(wrapped)
    }
    return sb.toString()
}
