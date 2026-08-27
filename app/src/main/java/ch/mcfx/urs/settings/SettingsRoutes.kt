package ch.mcfx.urs.settings

// Sub-routes within Settings, registered in the same top-level NavHost as
// Destination's routes (ch.mcfx.urs.navigation.AppNavigation) but
// deliberately not part of the Destination enum — they don't appear on the
// Home tile grid, only inside the Settings hub.
object SettingsRoutes {
    const val HUB = "settings/hub"
    const val GENERAL = "settings/general"
    const val DEFAULT_VEHICLE = "settings/default-vehicle"
    const val VPN = "settings/vpn"
    const val NOTIFICATIONS = "settings/notifications"
    const val ACCOUNT = "settings/account"
    const val WAGE_RULES = "settings/wage-rules"
    const val ABOUT = "settings/about"
    const val LOCATION_HISTORY = "settings/location-history"
    const val WATCH_RELAY = "settings/watch-relay"
    const val THEME = "settings/theme"
    const val LANGUAGE = "settings/language"
    const val PRODUCT_MANAGEMENT = "settings/product-management"
    const val ADMIN = "settings/admin"
    const val IMAGE_REVIEW = "settings/image-review"
    const val IMAGE_GENERATOR = "settings/image-generator"
}
