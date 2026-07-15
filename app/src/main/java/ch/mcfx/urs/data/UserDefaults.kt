package ch.mcfx.urs.data

/**
 * Hardcoded until this app has a real login/user concept — there is none
 * today. Every request that needs a user id uses this one; replace with the
 * auth-derived user id once authentication exists.
 */
object UserDefaults {
    const val DEFAULT_USER_ID = "2"
}
