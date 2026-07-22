package ch.mcfx.urs.lifemap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.LocationHistoryRepository
import ch.mcfx.urs.data.local.LocationHistoryDao
import ch.mcfx.urs.data.local.LocationHistoryEntity
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Approximate day-based windows rather than calendar-month arithmetic — good
 * enough for a browsing filter, and avoids picking a timezone/day-of-month
 * convention nothing else here needs to care about.
 */
enum class TimeRange(private val days: Long?) {
    LAST_MONTH(30),
    LAST_3_MONTHS(90),
    LAST_6_MONTHS(180),
    LAST_YEAR(365),
    ALL(null),
    ;

    fun toSinceMillis(): Long = days?.let { Instant.now().minus(it, ChronoUnit.DAYS).toEpochMilli() } ?: 0L
}

@OptIn(ExperimentalCoroutinesApi::class)
class LifeMapViewModel(
    private val locationHistoryDao: LocationHistoryDao,
    private val locationHistoryRepository: LocationHistoryRepository,
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(TimeRange.LAST_MONTH)
    val selectedRange: StateFlow<TimeRange> = _selectedRange.asStateFlow()

    private val _points = MutableStateFlow<List<LocationHistoryEntity>>(emptyList())
    val points: StateFlow<List<LocationHistoryEntity>> = _points.asStateFlow()

    init {
        // Pull the full server-side history down on load so points
        // captured on another install of this same account also show up
        // here — observeSince below only ever sees this device's local Room.
        viewModelScope.launch { locationHistoryRepository.refreshFromBackend() }
        viewModelScope.launch {
            _selectedRange
                .flatMapLatest { range -> locationHistoryDao.observeSince(range.toSinceMillis()) }
                .collect { _points.value = it }
        }
    }

    fun selectRange(range: TimeRange) {
        _selectedRange.value = range
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                LifeMapViewModel(app.container.database.locationHistoryDao(), app.container.locationHistoryRepository)
            }
        }
    }
}
