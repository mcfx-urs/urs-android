package ch.mcfx.urs.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.location.LocationCaptureScheduler
import ch.mcfx.urs.watchrelay.WatchRelayService
import kotlinx.coroutines.launch

// Exact alarms don't survive a reboot — this re-arms every persisted
// reminder, firing immediately first for any that were missed while the
// phone was off. rearmAndCheckMissed() is suspend (may need a network
// round-trip per conditional reminder), so this uses goAsync() the same
// way ReminderAlarmReceiver does. Also restarts the watch relay foreground
// service if it was left enabled — a foreground service does not survive a
// reboot on its own, unlike WorkManager's periodic work. Same reasoning
// covers Life Map's Precision mode: its exact alarm needs
// re-arming here too, since WorkManager-based capture (the non-precision
// path) already survives reboot on its own and needs no help.
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as UrsApplication
        if (app.container.watchRelaySettingsStore.isEnabled()) {
            WatchRelayService.start(app)
        }
        val locationHistorySettingsStore = app.container.locationHistorySettingsStore
        if (locationHistorySettingsStore.isEnabled() && locationHistorySettingsStore.isPrecisionModeEnabled()) {
            LocationCaptureScheduler.armExact(context, locationHistorySettingsStore.intervalMinutes())
        }
        val pendingResult = goAsync()
        app.container.applicationScope.launch {
            try {
                app.container.reminderScheduler.rearmAndCheckMissed()
                app.container.bakingRepository.rearmPendingStepAlarms()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
