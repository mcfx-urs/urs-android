package ch.mcfx.urs.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.UrsApplication

// Exact alarms don't survive a reboot — this re-arms every persisted
// reminder, firing immediately first for any that were missed while the
// phone was off.
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as UrsApplication
        app.container.reminderScheduler.rearmAndCheckMissed()
    }
}
