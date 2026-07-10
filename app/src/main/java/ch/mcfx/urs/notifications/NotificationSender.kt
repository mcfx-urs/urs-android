package ch.mcfx.urs.notifications

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ch.mcfx.urs.MainActivity
import ch.mcfx.urs.R

// The general "show a notification now" capability — usable by any feature
// for an immediate notification (an error, an informational message, or a
// fired reminder), independent of ReminderScheduler's daily-scheduling
// concern entirely.
class NotificationSender(private val context: Context) {

    // groupKey is optional: pass one when several related notifications may
    // be shown close together (e.g. several low-stock products at once) so
    // they collapse into a summary instead of flooding the shade one by one.
    fun show(
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        deepLinkRoute: String? = null,
        groupKey: String? = null,
    ) {
        val manager = NotificationManagerCompat.from(context)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(deepLinkPendingIntent(notificationId, deepLinkRoute))
        if (groupKey != null) builder.setGroup(groupKey)

        if (!manager.areNotificationsEnabled()) return
        // Distinct from areNotificationsEnabled() above: that's the user's
        // system-level "allow notifications from this app" toggle, this is
        // the API 33+ runtime POST_NOTIFICATIONS grant specifically. Checked
        // inline (not via a shared private helper) because lint's
        // MissingPermission dataflow check only recognizes a
        // checkSelfPermission() call directly guarding the same method's
        // own notify() call, not one hidden behind a wrapper function.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(notificationId, builder.build())

        if (groupKey != null) updateGroupSummary(manager, channelId, groupKey)
    }

    // Recomputes the summary from whatever's actually currently posted in
    // this group (via the system's own active-notification list) rather
    // than a manually tracked count — stays correct even if notifications
    // were dismissed independently, no separate bookkeeping to drift out of
    // sync.
    private fun updateGroupSummary(manager: NotificationManagerCompat, channelId: String, groupKey: String) {
        val activeInGroup = manager.activeNotifications.filter {
            it.notification.group == groupKey && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
        }
        if (activeInGroup.size < 2) return // one item alone doesn't need a summary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("${activeInGroup.size} new notifications")
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(groupKey.hashCode(), summary)
    }

    private fun deepLinkPendingIntent(requestCode: Int, deepLinkRoute: String?): PendingIntent {
        val intent = if (deepLinkRoute != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse("urs://$deepLinkRoute"), context, MainActivity::class.java)
        } else {
            Intent(context, MainActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
