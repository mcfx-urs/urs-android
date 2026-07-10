package ch.mcfx.urs.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.UrsApplication

// Fired by AlarmManager at the exact scheduled time. Looks up the
// reminder's current details (in case they changed since it was armed) and
// delegates to ReminderScheduler, which shows the notification and arms
// the next day's occurrence.
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        if (id == -1) return
        val app = context.applicationContext as UrsApplication
        val reminder = app.container.reminderStore.getAll().find { it.id == id } ?: return
        app.container.reminderScheduler.fireNow(reminder)
    }

    companion object {
        const val EXTRA_REMINDER_ID = "ch.mcfx.urs.notifications.REMINDER_ID"
    }
}
