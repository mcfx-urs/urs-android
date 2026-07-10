package ch.mcfx.urs.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.UrsApplication
import kotlinx.coroutines.launch

// Fired by AlarmManager at the exact scheduled time. Looks up the
// reminder's current details (in case they changed since it was armed) and
// delegates to ReminderScheduler, which shows the notification and arms
// the next day's occurrence. fireNow() is suspend (a conditional reminder
// needs a network round-trip to check its condition), so this uses
// goAsync() to keep the process alive past onReceive() returning — a plain
// coroutine launch here would otherwise risk being killed mid-flight.
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        if (id == -1) return
        val app = context.applicationContext as UrsApplication
        val pendingResult = goAsync()
        app.container.applicationScope.launch {
            try {
                val reminder = app.container.reminderStore.getAll().find { it.id == id }
                if (reminder != null) app.container.reminderScheduler.fireNow(reminder)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "ch.mcfx.urs.notifications.REMINDER_ID"
    }
}
