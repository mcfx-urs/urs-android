package ch.mcfx.urs.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "biometric_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_LAST_UNLOCK_AT = "last_unlock_at"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEYSTORE_ALIAS = "urs_biometric_gate_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private val UNLOCK_MARKER = "urs-biometric-unlock".toByteArray(Charsets.UTF_8)

/** How long a successful biometric unlock stays valid before the next app foreground demands a fresh one — the user's own call (2026-07-17), not the original design's "every app open". */
val BIOMETRIC_UNLOCK_VALIDITY_MS = 24 * 60 * 60 * 1000L

/**
 * Purely a *local* re-entry gate on top of the real session
 * ([AuthTokenStore]) — deliberately a separate Keystore key from the one
 * that actually encrypts the access/refresh tokens, because that one must
 * stay silently decryptable for [AuthInterceptor]/[AuthAuthenticator] to
 * attach/refresh tokens on background network calls, which can't block on
 * a fingerprint prompt. This key exists purely to *prove*, via
 * [android.security.keystore.KeyGenParameterSpec.Builder.setUserAuthenticationRequired],
 * that a real biometric ceremony happened — the value it
 * encrypts/decrypts is a meaningless constant, never the actual token.
 *
 * `setInvalidatedByBiometricEnrollment(true)` (the security-relevant
 * default) means Android permanently invalidates this key the moment the
 * device's biometric enrollment changes (new fingerprint added, all
 * removed, etc.) — [prepareCipher] surfaces that as a `null` return
 * *before* ever showing a prompt, so the caller can force a real
 * password re-login instead of a doomed biometric attempt (the user's
 * explicit requirement 2026-07-17: password is only ever needed again if
 * the device's biometric enrollment itself changes).
 */
class BiometricGate(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    val isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)

    /** True whenever biometric is enabled but either never unlocked this cycle or the 24h window has elapsed. */
    fun needsUnlock(): Boolean {
        if (!isEnabled) return false
        if (_isUnlocked.value) return false
        val lastUnlockAt = prefs.getLong(KEY_LAST_UNLOCK_AT, 0L)
        return System.currentTimeMillis() - lastUnlockAt >= BIOMETRIC_UNLOCK_VALIDITY_MS
    }

    /**
     * Re-locks (forces the next [needsUnlock] check to require a fresh
     * prompt) without touching whether biometric is enabled at all — call
     * whenever the app returns to the foreground, so the 24h window is
     * checked on every resume, not just a true cold start.
     */
    fun reevaluate() {
        if (needsUnlock()) _isUnlocked.value = false
    }

    /** Prepares a Cipher for [androidx.biometric.BiometricPrompt.CryptoObject] — null if the Keystore key was invalidated by an enrollment change (see class doc). */
    fun prepareCipher(): Cipher? {
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        }
    }

    /** Called from the BiometricPrompt success callback, with the same authorized Cipher [prepareCipher] returned. */
    fun confirmUnlock(cipher: Cipher) {
        cipher.doFinal(UNLOCK_MARKER)
        prefs.edit().putLong(KEY_LAST_UNLOCK_AT, System.currentTimeMillis()).apply()
        _isUnlocked.value = true
    }

    /** Turns biometric unlock on — called right after a successful enrollment-confirming BiometricPrompt from Settings, not before. */
    fun enable() {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
    }

    /** Turns biometric unlock off and discards the Keystore key — a fresh enable() later creates a new one. */
    fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).remove(KEY_LAST_UNLOCK_AT).apply()
        keyStore.deleteEntry(KEYSTORE_ALIAS)
        _isUnlocked.value = false
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
