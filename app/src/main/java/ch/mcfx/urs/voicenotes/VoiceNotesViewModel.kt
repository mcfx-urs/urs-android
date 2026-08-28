package ch.mcfx.urs.voicenotes

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.AudioNoteRepository
import ch.mcfx.urs.data.local.AudioNoteEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaybackState(
    val noteId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
)

data class VoiceNotesUiState(
    val notes: List<AudioNoteEntity> = emptyList(),
    val playback: PlaybackState = PlaybackState(),
)

class VoiceNotesViewModel(
    private val repository: AudioNoteRepository,
) : ViewModel() {

    private val playback = MutableStateFlow(PlaybackState())

    val uiState: StateFlow<VoiceNotesUiState> =
        combine(repository.observeAll(), playback) { notes, pb ->
            VoiceNotesUiState(notes = notes, playback = pb)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceNotesUiState())

    private var player: MediaPlayer? = null
    private var tickJob: Job? = null

    fun onPlayPause(note: AudioNoteEntity) {
        val current = playback.value
        if (current.noteId == note.id) {
            if (current.isPlaying) pause() else resume()
        } else {
            start(note)
        }
    }

    private fun start(note: AudioNoteEntity) {
        release()
        player = MediaPlayer().apply {
            setOnPreparedListener {
                it.start()
                playback.value = PlaybackState(noteId = note.id, isPlaying = true, positionMs = 0)
                startTicking()
            }
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ -> stop(); true }
            setDataSource(note.filePath)
            prepareAsync()
        }
    }

    private fun resume() {
        player?.start()
        playback.value = playback.value.copy(isPlaying = true)
        startTicking()
    }

    private fun pause() {
        player?.pause()
        tickJob?.cancel()
        playback.value = playback.value.copy(isPlaying = false, positionMs = player?.currentPosition ?: 0)
    }

    fun stop() {
        release()
        playback.value = PlaybackState()
    }

    fun delete(note: AudioNoteEntity) {
        if (playback.value.noteId == note.id) stop()
        viewModelScope.launch { repository.delete(note) }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val p = player ?: break
                playback.value = playback.value.copy(positionMs = p.currentPosition)
                delay(250)
            }
        }
    }

    private fun release() {
        tickJob?.cancel()
        tickJob = null
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    override fun onCleared() {
        release()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                VoiceNotesViewModel(app.container.audioNoteRepository)
            }
        }
    }
}
