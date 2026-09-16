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
import ch.mcfx.urs.location.GradientMode
import ch.mcfx.urs.location.LocationHistorySettingsStore
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Approximate day-based windows rather than calendar-month arithmetic — good
 * enough for a browsing filter, and avoids picking a timezone/day-of-month
 * convention nothing else here needs to care about.
 */
enum class TimeRange(private val days: Long?) {
    LAST_DAY(1),
    LAST_WEEK(7),
    LAST_MONTH(30),
    LAST_3_MONTHS(90),
    LAST_6_MONTHS(180),
    LAST_YEAR(365),
    ALL(null),
    ;

    fun toSinceMillis(): Long = days?.let { Instant.now().minus(it, ChronoUnit.DAYS).toEpochMilli() } ?: 0L
}

/**
 * Either one of the fixed [TimeRange] presets, or an explicit [Custom]
 * from/to instant pair picked via the custom-range sheet (GitHub issue #76)
 * — [LifeMapPointsState.range] and [LifeMapViewModel.selectedRange] use this
 * instead of a bare [TimeRange] so a custom pick and the preset dropdown can
 * share the same "what's currently active" slot. Picking a preset always
 * replaces an active custom pick and vice versa — there's no "both" state.
 */
sealed interface LifeMapRange {
    data class Preset(val range: TimeRange) : LifeMapRange
    data class Custom(val fromMillis: Long, val toMillis: Long) : LifeMapRange
}

/**
 * [points] bundled together with the exact [range] they were queried for —
 * never exposed as two independently-updating StateFlows. [selectedRange]
 * (below) updates the instant the user picks a new range, so the dropdown
 * label reacts immediately; but the Room query behind [observeSince] only
 * resolves a Flow-dispatch-and-query round trip later. If the map read
 * `selectedRange` and `points` as two separate StateFlows (as this used to
 * do), Compose would recompose once with (new range, still-old points) —
 * confirmed via logcat, e.g. `points.size` flipping 174→1661 across two
 * consecutive recompositions for one selection — and the map's own
 * zoom-to-bounding-box effect had no reliable way to tell that first,
 * mismatched recomposition apart from the real one: it raced
 * `mapView.post()` (UI message queue) against this Flow's coroutine
 * dispatch, two independent async mechanisms with no ordering guarantee
 * between them, so which one "won" and decided the final camera position
 * was pure timing luck — worked in some runs, silently stuck on a stale,
 * wrongly-zoomed viewport in others. Bundling them here means the map only
 * ever observes a (range, points) pair that was already consistent at
 * emission time — there is no intermediate mismatched state to race against.
 */
data class LifeMapPointsState(val range: LifeMapRange, val points: List<LocationHistoryEntity>)

/** Track rendering options, read from [LocationHistorySettingsStore] when the screen opens. */
data class TrackStyle(val gradientStops: List<Int>, val haloEnabled: Boolean)

/** Oldest-point brightness as a fraction of the base colour's own value — never fully black unless the base colour itself is. */
private const val INTENSITY_MIN_VALUE_FRACTION = 0.25f

/**
 * Two stops sharing [baseArgb]'s hue/saturation, varying only value (GitHub
 * issue #66) — dim for the oldest point, the base colour's own brightness for
 * the newest. Two stops are enough for a smooth ramp here (unlike the HUE
 * mode's three fixed hues): [LifeMapScreen]'s blendGradientStops already
 * linearly interpolates between them per segment.
 */
private fun intensityGradientStops(baseArgb: Int): List<Int> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(baseArgb, hsv)
    val dim = hsv.copyOf().also { it[2] *= INTENSITY_MIN_VALUE_FRACTION }
    val alpha = android.graphics.Color.alpha(baseArgb)
    return listOf(android.graphics.Color.HSVToColor(alpha, dim), baseArgb)
}

@OptIn(ExperimentalCoroutinesApi::class)
class LifeMapViewModel(
    private val locationHistoryDao: LocationHistoryDao,
    private val locationHistoryRepository: LocationHistoryRepository,
    private val settingsStore: LocationHistorySettingsStore,
) : ViewModel() {

    private val _selectedRange = MutableStateFlow<LifeMapRange>(LifeMapRange.Preset(TimeRange.LAST_DAY))
    val selectedRange: StateFlow<LifeMapRange> = _selectedRange.asStateFlow()

    private val _pointsState = MutableStateFlow(LifeMapPointsState(LifeMapRange.Preset(TimeRange.LAST_DAY), emptyList()))
    val pointsState: StateFlow<LifeMapPointsState> = _pointsState.asStateFlow()

    // Read once at construction — changing these lives in Location History
    // settings, which recreates this ViewModel on the way back here.
    val trackStyle: TrackStyle = TrackStyle(
        gradientStops = when (settingsStore.gradientMode()) {
            GradientMode.HUE -> settingsStore.trackColors()
            // Reuses the "newest" hue-mode stop as the intensity base colour
            // (see LocationHistorySettingsScreen) rather than a separate
            // stored colour — one fewer setting to keep in sync.
            GradientMode.INTENSITY -> intensityGradientStops(settingsStore.trackColors().last())
        },
        haloEnabled = settingsStore.isTrackHaloEnabled(),
    )

    private val _mutedMap = MutableStateFlow(settingsStore.isMutedMap())
    val mutedMap: StateFlow<Boolean> = _mutedMap.asStateFlow()

    init {
        // Pull the full server-side history down on load so points
        // captured on another install of this same account also show up
        // here — observeSince below only ever sees this device's local Room.
        viewModelScope.launch { locationHistoryRepository.refreshFromBackend() }
        viewModelScope.launch {
            _selectedRange
                .flatMapLatest { range ->
                    val points = when (range) {
                        is LifeMapRange.Preset -> locationHistoryDao.observeSince(range.range.toSinceMillis())
                        is LifeMapRange.Custom -> locationHistoryDao.observeBetween(range.fromMillis, range.toMillis)
                    }
                    points.map { LifeMapPointsState(range, it) }
                }
                .collect { _pointsState.value = it }
        }
    }

    fun selectRange(range: TimeRange) {
        _selectedRange.value = LifeMapRange.Preset(range)
    }

    fun selectCustomRange(fromMillis: Long, toMillis: Long) {
        _selectedRange.value = LifeMapRange.Custom(fromMillis, toMillis)
    }

    fun setMutedMap(muted: Boolean) {
        settingsStore.setMutedMap(muted)
        _mutedMap.value = muted
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                LifeMapViewModel(
                    app.container.database.locationHistoryDao(),
                    app.container.locationHistoryRepository,
                    app.container.locationHistorySettingsStore,
                )
            }
        }
    }
}
