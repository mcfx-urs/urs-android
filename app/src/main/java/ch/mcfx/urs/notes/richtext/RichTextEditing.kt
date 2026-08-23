package ch.mcfx.urs.notes.richtext

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Live editing operations on the rich-text [TextFieldValue] — everything the
 * field composable and toolbar call in response to a keystroke or a button
 * tap. [RichTextMarkup]'s parse/serialize functions are only used at the
 * load/save boundary; while the field is being edited, the canonical state
 * is this [AnnotatedString]-backed [TextFieldValue] itself.
 */

/**
 * Reconciles a raw [BasicTextField][androidx.compose.foundation.text.BasicTextField]
 * edit against the styled [current] value, then applies list/link editing
 * behavior (Enter/Backspace on a list line, auto-linkify). [new]'s own
 * `annotatedString` is not trusted for styling — only its plain text and
 * selection are used — since nothing here relies on `BasicTextField`
 * preserving spans across a keystroke by itself.
 */
internal fun applyRichTextEdit(current: TextFieldValue, new: TextFieldValue, pendingStyle: CharStyle): TextFieldValue {
    val (prefix, oldSuffixStart, newSuffixStart) = computeSplice(current.text, new.text)
    val insertedText = new.text.substring(prefix, newSuffixStart)
    val deletedLen = oldSuffixStart - prefix
    val spliced = spliceEdit(
        current.annotatedString,
        new.text,
        prefix,
        oldSuffixStart,
        newSuffixStart,
        if (insertedText.isNotEmpty()) pendingStyle else CharStyle(),
    )
    var value = TextFieldValue(spliced, new.selection, new.composition)

    // Position of the last character just inserted — used below for both the Enter/list
    // check and auto-linkify. Deliberately *not* `prefix + insertedText.length - 1` (the
    // diff-derived position): a plain prefix/suffix text diff is genuinely ambiguous
    // whenever the inserted character is identical to its neighbor, which is exactly what
    // happens pressing Enter anywhere except the very end of the note — the freshly typed
    // "\n" sits right before the *already-existing* "\n" ending that line, and the diff
    // can't tell which of the two adjacent, identical characters is the new one. It
    // resolves that ambiguity by greedily absorbing one character too many into the
    // common prefix, which silently pointed `handleEnterKey` at the wrong (empty) line
    // and dropped list continuation — but only when there was more content after the
    // line being edited, never at the end of the note, which is exactly the pattern
    // reported: list continuation worked at the end of a note but not in the middle of
    // one. `new.selection`, reported directly by the input system rather than inferred
    // from a text comparison, has no such ambiguity.
    val insertedEndPos = if (insertedText.isNotEmpty() && new.selection.collapsed) new.selection.start else prefix + insertedText.length

    // handleEnterKey's "exit list mode" branch removes the previous line's marker text
    // *before* `prefix`, shrinking the text and invalidating any offset computed against
    // the pre-call length — including the auto-linkify position below. Skip auto-linkify
    // for that edit rather than reusing a now-stale index (crashes with an
    // out-of-bounds access otherwise); there's nothing meaningful to linkify there anyway,
    // since what was removed was a list marker, not user-typed text.
    var skipAutoLinkify = false
    when {
        // `endsWith`, not an exact `== "\n"` match: some keyboards bundle extra characters
        // into the same edit as the Enter keystroke (e.g. a trailing space inserted while
        // auto-confirming the word just typed) — an exact-match check misses the newline
        // entirely on those, silently dropping list continuation since handleEnterKey never
        // gets called at all.
        insertedText.endsWith("\n") && deletedLen == 0 -> {
            val newlinePos = insertedEndPos - 1
            val beforeLength = value.text.length
            value = handleEnterKey(value, newlinePos)
            if (value.text.length < beforeLength) skipAutoLinkify = true
        }
        insertedText.isEmpty() && deletedLen == 1 -> value = handleBackspaceAtMarker(current, value, prefix)
    }
    if (!skipAutoLinkify && insertedText.isNotEmpty() && (insertedText.last() == ' ' || insertedText.last() == '\n')) {
        value = autoLinkify(value, insertedEndPos - 1)
    }
    return value
}

/** Common-prefix/common-suffix diff: (prefixLen, oldSuffixStart, newSuffixStart). */
private fun computeSplice(old: String, new: String): Triple<Int, Int, Int> {
    val maxPrefix = minOf(old.length, new.length)
    var prefix = 0
    while (prefix < maxPrefix && old[prefix] == new[prefix]) prefix++
    var oldEnd = old.length
    var newEnd = new.length
    while (oldEnd > prefix && newEnd > prefix && old[oldEnd - 1] == new[newEnd - 1]) {
        oldEnd--
        newEnd--
    }
    return Triple(prefix, oldEnd, newEnd)
}

// Splices the edit into the styled string by keeping the old prefix/suffix (with their spans
// intact, via AnnotatedString.subSequence) and inserting the new text plain, or under
// pendingStyle if one is armed — newly typed text never inherits an adjacent span's style
// implicitly, only the explicit pending-style toggle applies going forward.
private fun spliceEdit(
    old: AnnotatedString,
    newText: String,
    prefix: Int,
    oldSuffixStart: Int,
    newSuffixStart: Int,
    pendingStyle: CharStyle,
): AnnotatedString {
    val insertedText = newText.substring(prefix, newSuffixStart)
    val insertedAnnotated = when {
        insertedText.isEmpty() -> AnnotatedString("")
        pendingStyle.isPlain -> AnnotatedString(insertedText)
        else -> AnnotatedString(insertedText, spanStyles = listOf(AnnotatedString.Range(pendingStyle.toSpanStyle(), 0, insertedText.length)))
    }
    return old.subSequence(0, prefix) + insertedAnnotated + old.subSequence(oldSuffixStart, old.length)
}

// Enter on a non-empty list line continues the list at the same level/type, auto-incrementing a
// numbered marker off the current line's own number. Enter on an empty list line exits list mode
// instead of inserting another empty item (standard "double-enter exits list" behavior).
private fun handleEnterKey(value: TextFieldValue, newlinePos: Int): TextFieldValue {
    val annotated = value.annotatedString
    val text = annotated.text
    val prevLineStart = lineStart(text, newlinePos)
    val marker = annotated.listMarkerAt(prevLineStart) ?: return value
    val hasContent = marker.markerRange.end < newlinePos

    if (!hasContent) {
        val updated = annotated.subSequence(0, prevLineStart) + annotated.subSequence(marker.markerRange.end, annotated.length)
        return TextFieldValue(updated, TextRange(prevLineStart + 1))
    }

    val nextNumber = if (marker.type == ListType.NUMBER) currentMarkerNumber(text, marker) + 1 else null
    val insertPos = newlinePos + 1
    val indent = " ".repeat(marker.level * IndentSpacesPerLevel)
    val glyph = if (marker.type == ListType.BULLET) BulletGlyph else "$nextNumber. "
    val insertText = indent + glyph

    val builder = AnnotatedString.Builder(annotated.subSequence(0, insertPos))
    builder.append(insertText)
    val markerEnd = builder.length
    builder.addStringAnnotation(ListAnnotationTag, "${marker.type.name}:${marker.level}", insertPos, markerEnd)
    builder.append(annotated.subSequence(insertPos, annotated.length))
    return TextFieldValue(builder.toAnnotatedString(), TextRange(markerEnd))
}

// Backspacing the marker's own trailing space removes the whole marker in one step (dropping list
// mode for that line) instead of leaving a broken partial marker like "•" with no trailing space.
private fun handleBackspaceAtMarker(current: TextFieldValue, spliced: TextFieldValue, deletePos: Int): TextFieldValue {
    val ls = lineStart(current.text, deletePos + 1)
    val marker = current.annotatedString.listMarkerAt(ls) ?: return spliced
    if (deletePos != marker.markerRange.end - 1) return spliced

    val annotated = spliced.annotatedString
    val removeEnd = marker.markerRange.end - 1
    val updated = annotated.subSequence(0, ls) + annotated.subSequence(removeEnd, annotated.length)
    return TextFieldValue(updated, TextRange(ls))
}

private val UrlLikeRegex = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)

// Converts a bare URL-looking token into a link the moment it's followed by a space or line
// break, whether typed one character at a time or landing all at once via paste.
internal fun autoLinkify(value: TextFieldValue, separatorPos: Int): TextFieldValue {
    val text = value.text
    val lineBegin = lineStart(text, separatorPos)
    var tokenStart = separatorPos
    while (tokenStart > lineBegin && !text[tokenStart - 1].isWhitespace()) tokenStart--
    if (tokenStart >= separatorPos) return value

    val token = text.substring(tokenStart, separatorPos)
    if (!UrlLikeRegex.matches(token)) return value
    if (value.annotatedString.linkAt(tokenStart) != null) return value

    val url = if (token.startsWith("www.", ignoreCase = true)) "https://$token" else token
    val updated = value.annotatedString.replaceRangeWithLink(TextRange(tokenStart, separatorPos), token, url)
    return value.copy(annotatedString = updated)
}

/**
 * Toggles [flag] on [value]'s selection (or arms/disarms [pendingStyle] for
 * a collapsed cursor). Returns the possibly-updated field value alongside
 * the pending style to keep around for the next keystroke.
 */
internal fun toggleCharStyle(
    value: TextFieldValue,
    pendingStyle: CharStyle,
    flag: (CharStyle) -> Boolean,
    set: (CharStyle, Boolean) -> CharStyle,
): Pair<TextFieldValue, CharStyle> {
    val selection = value.selection
    if (selection.collapsed) {
        return value to set(pendingStyle, !flag(pendingStyle))
    }
    val target = TextRange(minOf(selection.start, selection.end), maxOf(selection.start, selection.end))
    val currentlyUniform = value.annotatedString.styleFlag(target, flag)
    val updated = value.annotatedString.withCharStyle(target) { set(it, !currentlyUniform) }
    return value.copy(annotatedString = updated) to pendingStyle
}

// Line-start offsets (in `value`'s current text) touched by its selection, or just the cursor's
// own line when the selection is collapsed.
private fun affectedLineStarts(text: String, selection: TextRange): List<Int> {
    val from = minOf(selection.start, selection.end)
    val to = maxOf(selection.start, selection.end)
    val starts = mutableListOf<Int>()
    var pos = lineStart(text, from)
    starts += pos
    var idx = text.indexOf('\n', pos)
    while (idx != -1 && idx < to) {
        pos = idx + 1
        starts += pos
        idx = text.indexOf('\n', pos)
    }
    return starts
}

// Applies a per-line edit (returning the updated string plus how many characters that line grew
// or shrank by) to every affected line in order, tracking the cumulative offset shift so each
// line is located correctly in the progressively-updated string, and keeping the selection
// anchored relative to the edits as they land.
private fun applyToLines(value: TextFieldValue, transformLine: (AnnotatedString, Int) -> Pair<AnnotatedString, Int>): TextFieldValue {
    var annotated = value.annotatedString
    val originalStarts = affectedLineStarts(annotated.text, value.selection)
    var delta = 0
    var selStart = value.selection.start
    var selEnd = value.selection.end
    for (originalStart in originalStarts) {
        val actualStart = originalStart + delta
        val (updated, lineDelta) = transformLine(annotated, actualStart)
        annotated = updated
        if (selStart >= actualStart) selStart += lineDelta
        if (selEnd >= actualStart) selEnd += lineDelta
        delta += lineDelta
    }
    return TextFieldValue(annotated, TextRange(selStart.coerceIn(0, annotated.length), selEnd.coerceIn(0, annotated.length)))
}

/** Activating one list type on a line deactivates the other one if it was active, per line touched by the selection. */
internal fun toggleList(value: TextFieldValue, type: ListType): TextFieldValue = applyToLines(value) { annotated, lineStart ->
    val marker = annotated.listMarkerAt(lineStart)
    if (marker != null && marker.type == type) {
        val updated = annotated.subSequence(0, lineStart) + annotated.subSequence(marker.markerRange.end, annotated.length)
        updated to -(marker.markerRange.end - marker.markerRange.start)
    } else {
        val level = marker?.level ?: 0
        val glyph = if (type == ListType.BULLET) BulletGlyph else "1. "
        val insertText = " ".repeat(level * IndentSpacesPerLevel) + glyph
        val withoutOldMarker = if (marker != null) {
            annotated.subSequence(0, lineStart) + annotated.subSequence(marker.markerRange.end, annotated.length)
        } else {
            annotated
        }
        val builder = AnnotatedString.Builder(withoutOldMarker.subSequence(0, lineStart))
        builder.append(insertText)
        val markerEnd = builder.length
        builder.addStringAnnotation(ListAnnotationTag, "${type.name}:$level", lineStart, markerEnd)
        builder.append(withoutOldMarker.subSequence(lineStart, withoutOldMarker.length))
        val updated = builder.toAnnotatedString()
        val delta = insertText.length - (marker?.let { it.markerRange.end - it.markerRange.start } ?: 0)
        updated to delta
    }
}

/** No-op on non-list lines; otherwise increases the level up to [MaxListLevel], restarting numbered items at 1. */
internal fun indentList(value: TextFieldValue): TextFieldValue = applyToLines(value) { annotated, lineStart ->
    val marker = annotated.listMarkerAt(lineStart)
    if (marker == null || marker.level >= MaxListLevel) {
        annotated to 0
    } else {
        rewriteMarker(annotated, lineStart, marker, marker.level + 1, resetNumber = true)
    }
}

/** No-op on non-list lines; decreases the level, or exits list mode entirely from level 0. */
internal fun outdentList(value: TextFieldValue): TextFieldValue = applyToLines(value) { annotated, lineStart ->
    val marker = annotated.listMarkerAt(lineStart)
    when {
        marker == null -> annotated to 0
        marker.level == 0 -> {
            val updated = annotated.subSequence(0, lineStart) + annotated.subSequence(marker.markerRange.end, annotated.length)
            updated to -(marker.markerRange.end - marker.markerRange.start)
        }
        else -> rewriteMarker(annotated, lineStart, marker, marker.level - 1, resetNumber = false)
    }
}

// Shared by indent/outdent: rewrites a line's marker (indent spaces + glyph) at `newLevel`,
// keeping the annotation range in sync. Numbered items either restart at 1 (indenting into a
// fresh sub-level) or resume off the nearest same-level sibling above (outdenting back into an
// already-numbered parent level) — this is a best-effort live estimate; the authoritative
// renumbering always happens at save time (see RichTextMarkup.serialize).
private fun rewriteMarker(annotated: AnnotatedString, lineStart: Int, marker: ListMarker, newLevel: Int, resetNumber: Boolean): Pair<AnnotatedString, Int> {
    val text = annotated.text
    val number = if (marker.type == ListType.NUMBER) {
        if (resetNumber) 1 else nearestSiblingNumber(text, annotated, lineStart, newLevel, marker.type)
    } else {
        null
    }
    val glyph = if (marker.type == ListType.BULLET) BulletGlyph else "$number. "
    val insertText = " ".repeat(newLevel * IndentSpacesPerLevel) + glyph

    val builder = AnnotatedString.Builder(annotated.subSequence(0, lineStart))
    builder.append(insertText)
    val markerEnd = builder.length
    builder.addStringAnnotation(ListAnnotationTag, "${marker.type.name}:$newLevel", lineStart, markerEnd)
    builder.append(annotated.subSequence(marker.markerRange.end, annotated.length))
    val updated = builder.toAnnotatedString()
    val delta = insertText.length - (marker.markerRange.end - marker.markerRange.start)
    return updated to delta
}

private fun nearestSiblingNumber(text: String, annotated: AnnotatedString, fromLineStart: Int, level: Int, type: ListType): Int {
    var searchFrom = fromLineStart
    while (searchFrom > 0) {
        val prevStart = lineStart(text, searchFrom - 1)
        val marker = annotated.listMarkerAt(prevStart) ?: return 1
        when {
            marker.level < level -> return 1
            marker.level == level -> return if (marker.type == type) currentMarkerNumber(text, marker) + 1 else 1
            else -> searchFrom = prevStart
        }
    }
    return 1
}
