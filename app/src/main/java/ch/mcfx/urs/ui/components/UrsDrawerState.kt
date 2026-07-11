package ch.mcfx.urs.ui.components

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** The two settled positions of [UrsNavigationDrawer] — replacement for `material3.DrawerValue`. */
enum class UrsDrawerValue { Closed, Open }

/**
 * Holds the [AnchoredDraggableState] backing [UrsNavigationDrawer] — replacement for
 * `material3.DrawerState`. The anchor positions themselves (in px) are only known once
 * [UrsNavigationDrawer] has measured the drawer panel's width, so they're wired up there via
 * [AnchoredDraggableState.updateAnchors], not here.
 */
class UrsDrawerState(initialValue: UrsDrawerValue = UrsDrawerValue.Closed) {
    val anchoredDraggableState = AnchoredDraggableState(initialValue = initialValue)

    /** Settled at [UrsDrawerValue.Closed] and not mid-animation towards [UrsDrawerValue.Open]. */
    val isClosed: Boolean
        get() = anchoredDraggableState.currentValue == UrsDrawerValue.Closed &&
            anchoredDraggableState.targetValue == UrsDrawerValue.Closed

    suspend fun open() = anchoredDraggableState.animateTo(UrsDrawerValue.Open)

    suspend fun close() = anchoredDraggableState.animateTo(UrsDrawerValue.Closed)
}

/** Creates and remembers a [UrsDrawerState], mirroring `rememberDrawerState`'s call-site ergonomics. */
@Composable
fun rememberUrsDrawerState(initialValue: UrsDrawerValue = UrsDrawerValue.Closed): UrsDrawerState =
    remember { UrsDrawerState(initialValue) }
