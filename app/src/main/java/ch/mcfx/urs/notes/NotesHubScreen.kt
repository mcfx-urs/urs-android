package ch.mcfx.urs.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.NoteWithTags
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FabIconStyle = TextStyle(fontSize = 28.sp)
private val ReminderDisplayFormat = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm")

/** Shared by [NoteRow] here and [ch.mcfx.urs.notes.NoteDetailScreen] — same display format as Baking's own plan-time display. */
internal fun formatReminder(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(ReminderDisplayFormat)

@Composable
fun NotesHubScreen(
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: NotesViewModel = viewModel(factory = NotesViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            NotesUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is NotesUiState.Data -> Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.l),
                    horizontalArrangement = Arrangement.End,
                ) {
                    UrsText(
                        text = stringResource(R.string.notes_history_link),
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.accent,
                        modifier = Modifier.clickable(onClick = onOpenHistory),
                    )
                }

                val allTags = remember(state.notes) { state.notes.flatMap { it.tags }.map { it.tagName }.distinct().sorted() }
                if (allTags.isNotEmpty()) {
                    TagFilterRow(tags = allTags, selected = tagFilter, onSelect = viewModel::setTagFilter)
                }

                val visible = if (tagFilter == null) state.notes else state.notes.filter { note -> note.tags.any { it.tagName == tagFilter } }
                NoteList(visible, onOpenNote)
            }
        }

        UrsFab(
            onClick = onNewNote,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
        ) {
            UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
        }
    }
}

@Composable
private fun TagFilterRow(tags: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FilterChip(stringResource(R.string.notes_filter_all_tags), selected == null) { onSelect(null) }
        tags.forEach { tag -> FilterChip(tag, selected == tag) { onSelect(tag) } }
    }
    Spacer(Modifier.height(Spacing.s))
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    UrsPill(
        text = label,
        containerColor = if (selected) UrsTheme.colors.accent else UrsTheme.colors.accent.copy(alpha = 0.15f),
        contentColor = if (selected) UrsTheme.colors.onAccent else UrsTheme.colors.accent,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun NoteList(notes: List<NoteWithTags>, onOpenNote: (String) -> Unit) {
    if (notes.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.notes_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ursScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(notes, key = { it.note.id }) { noteWithTags -> NoteRow(noteWithTags, onClick = { onOpenNote(noteWithTags.note.publicId) }) }
    }
}

@Composable
internal fun NoteRow(noteWithTags: NoteWithTags, onClick: () -> Unit) {
    UrsCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            UrsText(noteWithTags.note.title, style = UrsTheme.typography.cardTitle)
            noteWithTags.note.reminderAtMillis?.let {
                UrsText(formatReminder(it), style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)
            }
            if (noteWithTags.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    noteWithTags.tags.forEach { tag -> UrsPill(text = tag.tagName) }
                }
            }
        }
    }
}
