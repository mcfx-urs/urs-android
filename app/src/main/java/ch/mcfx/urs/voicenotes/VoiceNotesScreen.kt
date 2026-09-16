package ch.mcfx.urs.voicenotes

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.AudioNoteEntity
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm")

@Composable
fun VoiceNotesScreen(viewModel: VoiceNotesViewModel = viewModel(factory = VoiceNotesViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.notes.isEmpty()) {
            UrsText(
                stringResource(R.string.voice_notes_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = ursScreenContentPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(state.notes, key = { it.id }) { note ->
                    val isCurrent = state.playback.noteId == note.id
                    VoiceNoteRow(
                        note = note,
                        isPlaying = isCurrent && state.playback.isPlaying,
                        positionMs = if (isCurrent) state.playback.positionMs else 0,
                        onPlayPause = { viewModel.onPlayPause(note) },
                        onDelete = { viewModel.delete(note) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceNoteRow(
    note: AudioNoteEntity,
    isPlaying: Boolean,
    positionMs: Int,
    onPlayPause: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = UrsTheme.colors
    val context = LocalContext.current
    UrsCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UrsIconButton(
                onClick = onPlayPause,
                contentDescription = stringResource(
                    if (isPlaying) R.string.voice_notes_pause else R.string.voice_notes_play,
                ),
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                tint = colors.accent,
            )
            Spacer(Modifier.padding(horizontal = Spacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                UrsText(
                    Instant.ofEpochMilli(note.createdAtMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .format(DATE_FORMAT),
                    style = UrsTheme.typography.body,
                )
                Spacer(Modifier.height(Spacing.xs))
                UrsText(
                    text = if (isPlaying || positionMs > 0) {
                        "${formatMs(positionMs.toLong())} / ${formatMs(note.durationMs)}"
                    } else {
                        formatMs(note.durationMs)
                    },
                    style = UrsTheme.typography.caption,
                    color = colors.onSurfaceMuted,
                )
                if (isPlaying || positionMs > 0) {
                    Spacer(Modifier.height(Spacing.xs))
                    val fraction = if (note.durationMs > 0) {
                        (positionMs.toFloat() / note.durationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(colors.onSurfaceMuted.copy(alpha = 0.3f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(2.dp)
                                .background(colors.accent),
                        )
                    }
                }
            }
            UrsIconButton(
                onClick = { shareVoiceNote(context, note) },
                contentDescription = stringResource(R.string.voice_notes_share),
                imageVector = Icons.Filled.Share,
                tint = colors.onSurfaceMuted,
            )
            UrsIconButton(
                onClick = onDelete,
                contentDescription = stringResource(R.string.voice_notes_delete),
                imageVector = Icons.Filled.Delete,
                tint = colors.onSurfaceMuted,
            )
        }
    }
}

/**
 * Exposes the recording via the app's [FileProvider] (GitHub issue #59) —
 * `filesDir/audio-notes/` is app-private and otherwise unreachable to any
 * other app — and opens the system share sheet with a `content://` URI.
 * Never hands out the raw `file://` path.
 */
private fun shareVoiceNote(context: Context, note: AudioNoteEntity) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(note.filePath))
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/ogg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(sendIntent, null)) }
        .onFailure { Toast.makeText(context, R.string.voice_notes_share_no_app, Toast.LENGTH_SHORT).show() }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
