package ch.mcfx.urs.beer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.BeerRepository
import ch.mcfx.urs.data.remote.BeerLogDto
import ch.mcfx.urs.data.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

sealed interface BeerUiState {
    data object Loading : BeerUiState
    data class Error(val message: String) : BeerUiState
    data class Data(val entries: List<BeerLogDto>) : BeerUiState
}

class BeerViewModel(
    private val repository: BeerRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BeerUiState>(BeerUiState.Loading)
    val uiState: StateFlow<BeerUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = BeerUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = BeerUiState.Data(repository.getEntries())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = BeerUiState.Error(e.message ?: "unknown")
            }
        }
    }

    // Optimistic: a placeholder entry appears immediately so the charts and
    // stats card feel instant. logBeer only queues to the outbox, so the
    // syncNow() drains it against the backend when online (the reload then
    // shows the real row); offline it stays queued and the placeholder drops
    // out on reload, to reappear after a later successful sync.
    fun logBeer(amountMl: Int) {
        val now = LocalDateTime.now()
        val dateText = now.format(BeerStats.DATE_FORMAT)

        val state = _uiState.value
        if (state is BeerUiState.Data) {
            val placeholder = BeerLogDto(id = "pending-$now", amountMl = amountMl.toString(), date = dateText)
            _uiState.value = BeerUiState.Data(listOf(placeholder) + state.entries)
        }

        viewModelScope.launch {
            try {
                repository.logBeer(amountMl, dateText)
                syncManager.syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // fall through to reload — the placeholder simply drops out
            }
            load()
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteEntry(id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: the list keeps showing the entry if the delete
                // failed server-side; the user can just retry the tap.
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                BeerViewModel(app.container.beerRepository, app.container.syncManager)
            }
        }
    }
}
