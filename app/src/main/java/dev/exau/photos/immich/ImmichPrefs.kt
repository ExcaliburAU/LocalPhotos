package dev.exau.photos.immich

import android.content.Context
import android.provider.Settings

class ImmichPrefs(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("immich", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_URL, value.trim()).apply() }

    var email: String
        get() = prefs.getString(KEY_EMAIL, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_EMAIL, value.trim()).apply() }

    var accessToken: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_TOKEN, value).apply() }

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_API, value.trim()).apply() }

    var userName: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_USER, value).apply() }

    val authToken: String get() = accessToken.ifBlank { apiKey }

    val connected: Boolean get() = apiBase.isNotBlank() && authToken.isNotBlank()

    val imageAuthHeader: Pair<String, String>
        get() = if (accessToken.isNotBlank()) {
            "Authorization" to "Bearer $accessToken"
        } else {
            "x-api-key" to apiKey
        }

    val deviceId: String
        get() {
            val stored = prefs.getString(KEY_DEVICE, "").orEmpty()
            if (stored.isNotBlank()) return stored
            val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown"
            val id = "photos-$androidId"
            prefs.edit().putString(KEY_DEVICE, id).apply()
            return id
        }

    val apiBase: String get() = normalize(serverUrl)

    fun saveLogin(url: String, email: String, token: String, user: String) {
        prefs.edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, user)
            .remove(KEY_API)
            .apply()
    }

    fun clear() {
        val device = prefs.getString(KEY_DEVICE, "")
        val ids = prefs.getStringSet(KEY_BACKED_IDS, emptySet())?.toSet()
        val sums = prefs.getStringSet(KEY_BACKED_SUMS, emptySet())?.toSet()
        prefs.edit().clear().apply()
        val restore = prefs.edit()
        if (!device.isNullOrBlank()) restore.putString(KEY_DEVICE, device)
        if (!ids.isNullOrEmpty()) restore.putStringSet(KEY_BACKED_IDS, ids)
        if (!sums.isNullOrEmpty()) restore.putStringSet(KEY_BACKED_SUMS, sums)
        restore.apply()
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_EMAIL = "email"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_API = "api_key"
        private const val KEY_USER = "user"
        private const val KEY_DEVICE = "device_id"

        fun normalize(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (url.isBlank()) return ""
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            if (!url.endsWith("/api")) url = "$url/api"
            return url
        }

        private const val KEY_BACKED_IDS = "backed_up_ids"
        private const val KEY_BACKED_SUMS = "backed_up_checksums"
    }

    fun rememberedAssetIds(): Set<String> =
        prefs.getStringSet(KEY_BACKED_IDS, emptySet())?.toSet().orEmpty()

    fun rememberedChecksums(): Set<String> =
        prefs.getStringSet(KEY_BACKED_SUMS, emptySet())?.toSet().orEmpty()

    fun rememberUploaded(ids: Collection<String>, checksums: Collection<String> = emptyList()) {
        if (ids.isEmpty() && checksums.isEmpty()) return
        val nextIds = rememberedAssetIds() + ids
        val nextSums = rememberedChecksums() + checksums.map { it.lowercase() }
        prefs.edit()
            .putStringSet(KEY_BACKED_IDS, nextIds)
            .putStringSet(KEY_BACKED_SUMS, nextSums)
            .apply()
    }
}
