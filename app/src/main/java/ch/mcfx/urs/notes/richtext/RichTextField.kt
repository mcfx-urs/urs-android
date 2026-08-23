package ch.mcfx.urs.notes.richtext

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import ch.mcfx.urs.ui.components.UrsTextFieldChrome
import ch.mcfx.urs.ui.theme.UrsTheme
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.tokens.Spacing
import kotlinx.coroutines.launch

/**
 * Fixed total height of the toolbar-plus-text-area unit inside [RichTextField]. Deliberately
 * constant regardless of focus/toolbar-expanded state or how much text has been typed — the
 * text area below the toolbar rows scrolls internally within whatever's left of this budget
 * instead of growing the field itself. Without this, the field's own height (and so its
 * position within the outer form) changed every time the toolbar was toggled or enough text
 * was typed to add a line, which is what made the toolbar itself intermittently scroll out of
 * view on longer notes, and made the outer form's own scroll position generally unstable
 * while editing.
 */
private val ContentEditorHeight = 240.dp

/**
 * Editing state behind [RichTextField], hoisted out of that composable so
 * the link insert/edit popup can be rendered separately by [RichTextLinkSheetHost]
 * at the screen's top level — see that function's doc comment for why.
 * Create one via [rememberRichTextFieldState] and pass it to both.
 */
@Stable
class RichTextFieldState internal constructor(initialValue: String) {
    internal var fieldValue by mutableStateOf(TextFieldValue(RichTextMarkup.parse(initialValue)))
    internal var lastEmitted by mutableStateOf(initialValue)
    internal var pendingStyle by mutableStateOf(CharStyle())
    internal var linkRequest by mutableStateOf<LinkDialogRequest?>(null)

    internal fun commit(newValue: TextFieldValue, onValueChange: (String) -> Unit) {
        fieldValue = newValue
        val serialized = RichTextMarkup.serialize(newValue.annotatedString)
        lastEmitted = serialized
        onValueChange(serialized)
    }
}

/**
 * [value] is only re-parsed into [RichTextFieldState.fieldValue] when it
 * changes for a reason other than the field's own edits (e.g. the note
 * finishing an async load) — see the `lastEmitted` guard. Re-parsing on
 * every keystroke would both be wasteful and, more importantly, lose the
 * user's cursor position and any style not yet reflected by a round-trip.
 */
@Composable
fun rememberRichTextFieldState(value: String): RichTextFieldState {
    val state = remember { RichTextFieldState(value) }
    LaunchedEffect(value) {
        if (value != state.lastEmitted) {
            state.fieldValue = TextFieldValue(RichTextMarkup.parse(value))
            state.lastEmitted = value
        }
    }
    return state
}

/**
 * Rich-text replacement for the plain `UrsTextField` used on the note
 * content field. Owns markup parsing/serialization internally ([RichTextMarkup])
 * and exposes the same plain-`String` `onValueChange` contract as
 * `UrsTextField` — the caller (and the note's storage) never sees the
 * styled model. [state] is created once by the caller via
 * [rememberRichTextFieldState] and shared with [RichTextLinkSheetHost].
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun RichTextField(
    state: RichTextFieldState,
    onValueChange: (String) -> Unit,
    label: String,
    /** The ancestor form's own scroll state (`NoteForm`'s `ursFormScrollPadding`) — driven directly, see below. */
    formScrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var editorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textBoxHeightPx by remember { mutableStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val textScrollState = rememberScrollState()
    val density = LocalDensity.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val marginPx = with(density) { Spacing.xl.toPx() }
    val imeInsets = WindowInsets.imeAnimationTarget

    // BasicTextField has its own built-in "scroll the newly focused field into view"
    // behavior — confirmed present (independent of any code in this file) by watching it
    // fire even with every custom scroll attempt below temporarily removed. The problem
    // wasn't that it existed, it's that (when the field could grow to any height) it fired
    // immediately on focus, before the keyboard had reached its final height, targeting
    // the field's own bounds rather than the cursor specifically — producing its own
    // separate, incomplete movement.
    //
    // Now that the toolbar-plus-text unit has a fixed [ContentEditorHeight] regardless of
    // focus/toolbar state, that same built-in mechanism is actually the right tool again
    // for the *inner* text scroll — keeping the cursor visible within this small, static
    // box as you type is exactly the well-supported case it's built for, so it's left
    // alone there. `suppressBuiltInScroll` is attached one level higher instead, wrapping
    // the whole fixed-height unit: it still lets the inner text scroll (`textScrollState`)
    // handle/consume the request locally, but stops it from propagating any further up
    // into the outer, keyboard-sensitive form — which is the level that was actually
    // fighting a moving target before. `scrollToCursor` below is the only thing that
    // scrolls the outer form, and now only needs to reveal this whole static box once on
    // focus, not chase the cursor on every keystroke.
    val suppressBuiltInScroll = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect = localRect
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {}
        }
    }

    fun scrollToEditor() {
        val coordinates = editorCoordinates ?: return
        if (!focused || !coordinates.isAttached) return
        val editorTop = coordinates.localToWindow(Offset.Zero).y - marginPx
        val editorBottom = coordinates.localToWindow(Offset(0f, coordinates.size.height.toFloat())).y + marginPx
        val viewportBottom = view.height - imeInsets.getBottom(density)
        val delta = when {
            editorBottom > viewportBottom -> editorBottom - viewportBottom
            editorTop < 0f -> editorTop
            else -> 0f
        }
        if (delta != 0f) {
            coroutineScope.launch { formScrollState.animateScrollBy(delta) }
        }
    }

    // Only needs to react to focus (and the ime target settling, see the comment on the
    // earlier version of this effect above) — the editor's own position never changes
    // once focused, unlike before when it had to re-run on every keystroke to chase a
    // field that kept growing.
    LaunchedEffect(focused, imeInsets.getBottom(density)) {
        scrollToEditor()
    }

    // BasicTextField's own built-in "scroll into view" behavior turns out to only ever
    // fire once, on focus — not again as the text keeps growing while already focused
    // (confirmed: with all custom scroll code removed for a diagnostic build, typing new
    // lines never scrolled at all, only the initial tap did anything). So the inner text
    // scroll needs the same kind of explicit per-edit correction the outer form used to
    // need — just against `textScrollState` instead, and much simpler: this box's own
    // height is fixed and never resizes for the keyboard, so there's no moving target to
    // chase, no ime timing to reason about, just "is the cursor within the currently
    // visible slice of `textScrollState`, and if not, scroll by the difference".
    fun scrollTextToCursor() {
        val layout = textLayout ?: return
        if (!focused || textBoxHeightPx <= 0f) return
        val cursorOffset = state.fieldValue.selection.end.coerceIn(0, layout.layoutInput.text.length)
        val cursorRect = layout.getCursorRect(cursorOffset)
        val visibleTop = textScrollState.value.toFloat()
        val visibleBottom = visibleTop + textBoxHeightPx
        val delta = when {
            cursorRect.bottom > visibleBottom -> cursorRect.bottom - visibleBottom
            cursorRect.top < visibleTop -> cursorRect.top - visibleTop
            else -> 0f
        }
        if (delta != 0f) {
            coroutineScope.launch { textScrollState.animateScrollBy(delta) }
        }
    }

    LaunchedEffect(focused, state.fieldValue.selection, textLayout) {
        scrollTextToCursor()
    }

    LaunchedEffect(focused) {
        if (!focused) {
            state.pendingStyle = CharStyle()
        }
    }

    fun commit(newValue: TextFieldValue) = state.commit(newValue, onValueChange)

    Column(modifier = modifier) {
        UrsTextFieldChrome(
            label = label,
            modifier = Modifier.fillMaxWidth(),
            supportingText = null,
            interactionSource = interactionSource,
        ) { colors ->
            // Fixed height regardless of focus/toolbar state — see [ContentEditorHeight].
            // `suppressBuiltInScroll`/`onGloballyPositioned` sit here (wrapping toolbar +
            // text together as one static-position unit), not on the text field itself, so
            // the inner text scroll below is free to use BasicTextField's own built-in
            // cursor-follow behavior unsuppressed.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ContentEditorHeight)
                    .bringIntoViewResponder(suppressBuiltInScroll)
                    .onGloballyPositioned { editorCoordinates = it },
            ) {
                if (focused) {
                    val selection = state.fieldValue.selection
                    val target = TextRange(minOf(selection.start, selection.end), maxOf(selection.start, selection.end))
                    val activeBold = if (selection.collapsed) state.pendingStyle.bold else state.fieldValue.annotatedString.styleFlag(target) { it.bold }
                    val activeItalic = if (selection.collapsed) state.pendingStyle.italic else state.fieldValue.annotatedString.styleFlag(target) { it.italic }
                    val activeUnderline = if (selection.collapsed) state.pendingStyle.underline else state.fieldValue.annotatedString.styleFlag(target) { it.underline }

                    RichTextToolbar(
                        activeBold = activeBold,
                        activeItalic = activeItalic,
                        activeUnderline = activeUnderline,
                        onBold = {
                            val (updated, style) = toggleCharStyle(state.fieldValue, state.pendingStyle, { it.bold }) { s, v -> s.copy(bold = v) }
                            state.pendingStyle = style
                            commit(updated)
                        },
                        onItalic = {
                            val (updated, style) = toggleCharStyle(state.fieldValue, state.pendingStyle, { it.italic }) { s, v -> s.copy(italic = v) }
                            state.pendingStyle = style
                            commit(updated)
                        },
                        onUnderline = {
                            val (updated, style) = toggleCharStyle(state.fieldValue, state.pendingStyle, { it.underline }) { s, v -> s.copy(underline = v) }
                            state.pendingStyle = style
                            commit(updated)
                        },
                        onLink = {
                            val prefill = if (!target.collapsed) state.fieldValue.text.substring(target.start, target.end) else ""
                            state.linkRequest = LinkDialogRequest.Insert(target, prefill)
                        },
                        onBullet = { commit(toggleList(state.fieldValue, ListType.BULLET)) },
                        onNumbered = { commit(toggleList(state.fieldValue, ListType.NUMBER)) },
                        onIndent = { commit(indentList(state.fieldValue)) },
                        onOutdent = { commit(outdentList(state.fieldValue)) },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { textBoxHeightPx = it.size.height.toFloat() }
                        .verticalScroll(textScrollState),
                ) {
                    BasicTextField(
                        value = state.fieldValue.copy(annotatedString = state.fieldValue.annotatedString.withLinkVisuals(colors.accent)),
                        onValueChange = { new -> commit(applyRichTextEdit(state.fieldValue, new, state.pendingStyle)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            // BasicTextField only ever takes the height its own text content
                            // needs — on a short/empty note that's far less than this fixed
                            // editor box, leaving a big tappable-looking area (the rest of the
                            // box) that's actually just the Box's own background, nothing there
                            // to receive the tap or gain focus. `fillMaxHeight()` isn't an
                            // option here (this Box's content has unbounded height via
                            // `verticalScroll`, and Compose disallows filling an infinite max
                            // height); a minimum pinned to the box's own known measured height
                            // achieves the same "always at least fills the visible area" result
                            // without conflicting with growing taller than it for longer notes.
                            .heightIn(min = with(density) { textBoxHeightPx.toDp() })
                            .pointerInput(state.fieldValue.annotatedString, textLayout) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                    if (up != null) {
                                        val offset = textLayout?.getOffsetForPosition(down.position)
                                        val link = offset?.let { state.fieldValue.annotatedString.linkAt(it) }
                                        if (link != null) {
                                            state.linkRequest = LinkDialogRequest.Edit(
                                                link.range,
                                                state.fieldValue.text.substring(link.range.start, link.range.end),
                                                link.url,
                                            )
                                        }
                                    }
                                }
                            },
                        textStyle = UrsTheme.typography.body.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.accent),
                        interactionSource = interactionSource,
                        onTextLayout = { textLayout = it },
                    )
                }
            }
        }
    }
}

/**
 * Renders [state]'s pending link insert/edit popup, if any. Call this once,
 * at the screen's top level — a sibling of the main scrollable form, the
 * same placement `NoteDetailScreen` already uses for its delete-confirmation
 * sheet — never nested inside [RichTextField] itself.
 *
 * [UrsBottomSheet][ch.mcfx.urs.ui.components.UrsBottomSheet] sizes itself
 * against whatever constraints its immediate parent hands it. Composed
 * inside [RichTextField], which lives in the form's scrollable column,
 * those constraints are the content field's own local bounds rather than
 * the actual screen — squeezing the sheet into a thin sliver above the
 * keyboard instead of a proper full-screen overlay. At the screen's top
 * level (a direct child of the top-level `Box`, outside the scrollable
 * column) it gets the real screen constraints, exactly like the existing
 * delete-confirmation sheet already relies on.
 */
@Composable
fun RichTextLinkSheetHost(state: RichTextFieldState, onValueChange: (String) -> Unit) {
    val request = state.linkRequest ?: return
    val context = LocalContext.current

    LinkEditSheet(
        initialText = request.prefillText,
        initialUrl = if (request is LinkDialogRequest.Edit) request.url else "",
        isExisting = request is LinkDialogRequest.Edit,
        onSave = { text, url ->
            val range = when (request) {
                is LinkDialogRequest.Insert -> request.range
                is LinkDialogRequest.Edit -> request.range
            }
            val updated = state.fieldValue.annotatedString.replaceRangeWithLink(range, text, normalizeUrl(url))
            state.commit(TextFieldValue(updated, TextRange(range.start + text.length)), onValueChange)
            state.linkRequest = null
        },
        onRemove = {
            if (request is LinkDialogRequest.Edit) {
                state.commit(state.fieldValue.copy(annotatedString = state.fieldValue.annotatedString.withoutLink(request.range)), onValueChange)
            }
            state.linkRequest = null
        },
        onOpen = {
            if (request is LinkDialogRequest.Edit) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(request.url)))) }
            }
        },
        onDismiss = { state.linkRequest = null },
    )
}

internal sealed class LinkDialogRequest {
    abstract val prefillText: String

    data class Insert(val range: TextRange, override val prefillText: String) : LinkDialogRequest()
    data class Edit(val range: TextRange, override val prefillText: String, val url: String) : LinkDialogRequest()
}

private fun normalizeUrl(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.contains("://")) trimmed else "https://$trimmed"
}
