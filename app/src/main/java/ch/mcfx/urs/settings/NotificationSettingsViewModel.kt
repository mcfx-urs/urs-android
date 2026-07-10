package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.notifications.NotificationChannels
import ch.mcfx.urs.notifications.NotificationSender
import ch.mcfx.urs.notifications.ReminderScheduler

class NotificationSettingsViewModel(
    private val reminderScheduler: ReminderScheduler,
    private val notificationSender: NotificationSender,
) : ViewModel() {

    fun canScheduleExactAlarms(): Boolean = reminderScheduler.canScheduleExactAlarms()

    // Proves the general "show a notification now" capability end-to-end
    // without needing a real consumer feature (e.g. Inventory's low-stock
    // reminder) to exist yet.
    fun sendTestNotification() {
        notificationSender.show(
            channelId = NotificationChannels.INVENTORY,
            notificationId = TEST_NOTIFICATION_ID,
            title = "Test notification",
            body = "If you can see this, notifications are working.",
        )
    }

    companion object {
        private const val TEST_NOTIFICATION_ID = 999_001

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                NotificationSettingsViewModel(app.container.reminderScheduler, app.container.notificationSender)
            }
        }
    }
}
