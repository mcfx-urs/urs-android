package ch.mcfx.urs.settings

// Sub-routes within Settings, registered in the same top-level NavHost as
// Destination's routes (ch.mcfx.urs.navigation.AppNavigation) but
// deliberately not part of the Destination enum — they don't appear in the
// drawer or the Home tile grid, only inside the Settings hub.
object SettingsRoutes {
    const val HUB = "settings/hub"
    const val VPN = "settings/vpn"
    const val NOTIFICATIONS = "settings/notifications"
    const val WORK_TIME = "settings/work-time"
    const val ACCOUNT = "settings/account"
    const val ABOUT = "settings/about"
    const val LOCATION_HISTORY = "settings/location-history"
}
