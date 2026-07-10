package ch.mcfx.urs.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.UrsApplication
import kotlinx.coroutines.launch

// Exact alarms don't survive a reboot — this re-arms every persisted
// reminder, firing immediately first for any that were missed while the
// phone was off. rearmAndCheckMissed() is suspend (may need a network
// round-trip per conditional reminder), so this uses goAsync() the same
// way ReminderAlarmReceiver does.
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as UrsApplication
        val pendingResult = goAsync()
        app.container.applicationScope.launch {
            try {
                app.container.reminderScheduler.rearmAndCheckMissed()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
