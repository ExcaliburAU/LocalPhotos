package dev.exau.photos.files

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SambaPrefs(context: Context) {
    private val prefs = secureOrPlain(context.applicationContext)

    fun shares(): List<SambaShare> {
        val raw = prefs.getString(KEY, "[]").orEmpty()
        val arr = JSONArray(raw)
        val list = ArrayList<SambaShare>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list += SambaShare(
                id = o.getString("id"),
                host = o.optString("host"),
                share = o.optString("share"),
                username = o.optString("username"),
                password = o.optString("password"),
                domain = o.optString("domain"),
                port = o.optInt("port", 445),
            )
        }
        return list
    }

    fun upsert(share: SambaShare): List<SambaShare> {
        val next = shares().filterNot { it.id == share.id } + share
        save(next)
        return next
    }

    fun remove(id: String): List<SambaShare> {
        val next = shares().filterNot { it.id == id }
        save(next)
        return next
    }

    fun newShare(
        host: String,
        share: String,
        username: String,
        password: String,
    ): SambaShare {
        val target = parseTarget(host, share)
        val account = parseUser(username, "")
        return SambaShare(
            id = UUID.randomUUID().toString(),
            host = target.host,
            share = target.share,
            username = account.second,
            password = password,
            domain = account.first,
            port = target.port,
        )
    }

    private fun save(list: List<SambaShare>) {
        val arr = JSONArray()
        list.forEach { share ->
            arr.put(
                JSONObject()
                    .put("id", share.id)
                    .put("host", share.host)
                    .put("share", share.share)
                    .put("username", share.username)
                    .put("password", share.password)
                    .put("domain", share.domain)
                    .put("port", share.port),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "shares"
        private const val PLAIN = "samba"
        private const val SECURE = "samba_secure"

        data class Target(val host: String, val share: String, val port: Int)

        private fun secureOrPlain(context: Context): SharedPreferences {
            return try {
                val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                val encrypted = EncryptedSharedPreferences.create(
                    SECURE,
                    key,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                migratePlaintext(context, encrypted)
                encrypted
            } catch (_: Exception) {
                context.getSharedPreferences(PLAIN, Context.MODE_PRIVATE)
            }
        }

        private fun migratePlaintext(context: Context, encrypted: SharedPreferences) {
            val plain = context.getSharedPreferences(PLAIN, Context.MODE_PRIVATE)
            val old = plain.getString(KEY, null) ?: return
            if (!encrypted.contains(KEY)) {
                encrypted.edit().putString(KEY, old).apply()
            }
            plain.edit().clear().apply()
        }

        fun parseTarget(hostRaw: String, shareRaw: String): Target {
            var rest = hostRaw.trim()
            rest = rest.removePrefix("smb://").removePrefix("SMB://")
            rest = rest.removePrefix("\\\\").removePrefix("//")
            rest = rest.replace('\\', '/')
            val hostPort = rest.substringBefore('/')
            val after = rest.substringAfter('/', missingDelimiterValue = "")
            val shareFromHost = after.substringBefore('/').trim()
            val host: String
            val port: Int
            if (hostPort.contains(':')) {
                host = hostPort.substringBefore(':').trim()
                port = hostPort.substringAfter(':').toIntOrNull() ?: 445
            } else {
                host = hostPort.trim()
                port = 445
            }
            val share = shareRaw.trim().trim('/').ifBlank { shareFromHost }
            return Target(host, share, port)
        }

        fun parseUser(raw: String, domainField: String): Pair<String, String> {
            val value = raw.trim()
            return when {
                value.contains('\\') -> value.substringBefore('\\').trim() to value.substringAfter('\\').trim()
                value.contains('/') -> value.substringBefore('/').trim() to value.substringAfter('/').trim()
                value.contains('@') -> value.substringAfter('@').trim() to value.substringBefore('@').trim()
                else -> domainField.trim() to value
            }
        }
    }
}
