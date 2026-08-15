package dev.exau.photos.lock

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import java.security.SecureRandom

class AppLock(context: Context) {
    private val prefs = secureOrPlain(context.applicationContext)

    val enabled: Boolean
        get() = prefs.getString(PIN, null)?.isNotBlank() == true

    fun setPin(pin: String) {
        val clean = pin.trim()
        if (clean.length < 4) error("Use at least 4 digits")
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(SALT, salt.toHex())
            .putString(PIN, hash(clean, salt))
            .apply()
    }

    fun clear() {
        prefs.edit().remove(PIN).remove(SALT).apply()
    }

    fun matches(pin: String): Boolean {
        val stored = prefs.getString(PIN, null) ?: return false
        val salt = prefs.getString(SALT, null)?.fromHex() ?: return false
        return MessageDigest.isEqual(stored.toByteArray(), hash(pin.trim(), salt).toByteArray())
    }

    companion object {
        private const val FILE = "app_lock"
        private const val PLAIN = "app_lock_plain"
        private const val PIN = "pin"
        private const val SALT = "salt"

        private fun hash(pin: String, salt: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            digest.update(pin.toByteArray())
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun String.fromHex(): ByteArray =
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        private fun secureOrPlain(context: Context): SharedPreferences {
            return try {
                val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    FILE,
                    key,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                context.getSharedPreferences(PLAIN, Context.MODE_PRIVATE)
            }
        }
    }
}
