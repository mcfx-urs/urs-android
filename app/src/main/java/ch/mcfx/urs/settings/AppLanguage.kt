package ch.mcfx.urs.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * App-internal language override — independent of the device's own system
 * language setting. Backed directly by the platform's per-app language API
 * (`LocaleManager`, API 33+) — minSdk is 34, so no AppCompat backport for
 * older devices is needed.
 *
 * `gsw` is the ISO 639-3 code for Swiss German — there is no standard
 * system-level "Schweizerdeutsch" language on Android (only "Deutsch
 * (Schweiz)", which is Standard German with Swiss conventions), so this
 * has to be an in-app choice rather than following the system language.
 */
enum class AppLanguage(val languageTag: String) {
    ENGLISH("en"),
    SCHWIIZERDUTSCH("gsw"),
}

fun setAppLanguage(context: Context, language: AppLanguage) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        LocaleList.forLanguageTags(language.languageTag)
}

/** Falls back to [AppLanguage.ENGLISH] if nothing has been explicitly set yet. */
fun currentAppLanguage(context: Context): AppLanguage {
    val current = context.getSystemService(LocaleManager::class.java).applicationLocales
    return AppLanguage.entries.firstOrNull { current.toLanguageTags().contains(it.languageTag) } ?: AppLanguage.ENGLISH
}
