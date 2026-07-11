package ch.mcfx.urs.data.local

/**
 * First-launch-before-any-successful-backend-sync fallback so the currency
 * picker still works fully offline (seeded once, only if the local cache is
 * still empty — see [CurrencyDao.count]). A subset of the backend's own
 * seed list, not required to be exhaustive: [ch.mcfx.urs.data.FuelRepository
 * .refreshFromBackend] replaces this with the server's authoritative list as
 * soon as it's reachable.
 */
object BundledCurrencies {
    val seed = listOf(
        CurrencyEntity("CHF", "Swiss Franc"),
        CurrencyEntity("EUR", "Euro"),
        CurrencyEntity("USD", "US Dollar"),
        CurrencyEntity("GBP", "British Pound"),
        CurrencyEntity("JPY", "Japanese Yen"),
        CurrencyEntity("CAD", "Canadian Dollar"),
        CurrencyEntity("AUD", "Australian Dollar"),
        CurrencyEntity("SEK", "Swedish Krona"),
        CurrencyEntity("NOK", "Norwegian Krone"),
        CurrencyEntity("DKK", "Danish Krone"),
        CurrencyEntity("CZK", "Czech Koruna"),
        CurrencyEntity("PLN", "Polish Zloty"),
        CurrencyEntity("HUF", "Hungarian Forint"),
        CurrencyEntity("TRY", "Turkish Lira"),
        CurrencyEntity("AED", "UAE Dirham"),
    )
}
