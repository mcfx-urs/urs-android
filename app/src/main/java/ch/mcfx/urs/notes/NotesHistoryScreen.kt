package ch.mcfx.urs.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.ui.components.UrsConfirmBar
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import kotlinx.coroutines.delay

@Composable
fun NotesHistoryScreen(
    onOpenNote: (String) -> Unit,
    /** True right after a note was reopened in the detail screen; the bar is shown until [onNoteReopenedShown]. */
    noteReopened: Boolean = false,
    onNoteReopenedShown: () -> Unit = {},
    viewModel: NotesHistoryViewModel = viewModel(factory = NotesHistoryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(noteReopened) {
        if (noteReopened) {
            delay(ReopenConfirmationTimeoutMillis)
            onNoteReopenedShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            NotesUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is NotesUiState.Data -> if (state.notes.isEmpty()) {
                UrsText(
                    stringResource(R.string.notes_history_empty),
                    modifier = Modifier.align(Alignment.Center),
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = ursScreenContentPadding(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(state.notes, key = { it.note.id }) { noteWithTags ->
                        NoteRow(noteWithTags, onClick = { onOpenNote(noteWithTags.note.publicId) })
                    }
                }
            }
        }

        if (noteReopened) {
            UrsConfirmBar(
                message = stringResource(R.string.note_reopened_confirmation),
                modifier = Modifier.align(Alignment.TopCenter).padding(Spacing.l),
            )
        }
    }
}

// How long the note-reopened confirmation bar stays up before it dismisses itself.
private const val ReopenConfirmationTimeoutMillis = 2500L
