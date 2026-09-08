package ch.mcfx.urs.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import ch.mcfx.urs.UrsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Done" action on a fired note-reminder notification (GitHub issue #52) —
 * clears just the note's reminder ([ch.mcfx.urs.data.NoteRepository.clearReminder]),
 * leaving its completion status untouched. Unlike [NoteAlarmReceiver], this
 * needs goAsync(): clearing the reminder is a suspend Room write, and a
 * BroadcastReceiver's process can otherwise be killed before it completes.
 */
class NoteReminderDoneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(NoteAlarmReceiver.EXTRA_NOTE_ID) ?: return
        val notificationId = intent.getIntExtra(NoteAlarmReceiver.EXTRA_ALARM_ID, -1)
        val app = context.applicationContext as UrsApplication
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val localId = app.container.noteRepository.resolveLocalNoteId(noteId)
                if (localId != null) app.container.noteRepository.clearReminder(localId)
            } finally {
                if (notificationId >= 0) NotificationManagerCompat.from(context).cancel(notificationId)
                pendingResult.finish()
            }
        }
    }
}
