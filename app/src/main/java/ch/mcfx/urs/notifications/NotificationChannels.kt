package ch.mcfx.urs.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat

// One channel per feature area, so the user can mute one without affecting
// others. Add a new entry here when a new feature area starts sending
// notifications — nothing else needs to change to register it.
object NotificationChannels {

    const val INVENTORY = "inventory"
    const val WATCH_RELAY = "watch_relay"

    private data class ChannelDef(
        val id: String,
        val name: String,
        val description: String,
        val importance: Int,
    )

    private val ALL = listOf(
        ChannelDef(
            id = INVENTORY,
            name = "Inventory",
            description = "Low-stock and other Inventory reminders",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        ),
        ChannelDef(
            id = WATCH_RELAY,
            name = "Watch relay",
            description = "Ongoing status notification while the watch relay is active",
            importance = NotificationManager.IMPORTANCE_LOW,
        ),
    )

    fun registerAll(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        ALL.forEach { def ->
            val channel = NotificationChannel(def.id, def.name, def.importance).apply {
                description = def.description
            }
            manager.createNotificationChannel(channel)
        }
    }
}
