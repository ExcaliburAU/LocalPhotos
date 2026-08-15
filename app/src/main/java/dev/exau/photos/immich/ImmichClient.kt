package dev.exau.photos.immich

import android.content.ContentResolver
import dev.exau.photos.data.MediaItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

data class ImmichAsset(
    val id: String,
    val fileName: String,
    val isVideo: Boolean,
    val createdAt: Long,
    val thumbUrl: String,
    val previewUrl: String,
    val originalUrl: String,
)

data class ImmichUser(
    val name: String,
    val email: String,
)

data class ImmichAssetPage(
    val items: List<ImmichAsset>,
    val nextPage: Int?,
)

class ImmichClient(private val prefs: ImmichPrefs) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    fun login(url: String, email: String, password: String): ImmichUser {
        val base = ImmichPrefs.normalize(url)
        val emailValue = email.trim().lowercase()
        val passwordValue = password
        if (base.isBlank()) error("Enter the server address")
        if (emailValue.isBlank() || passwordValue.isBlank()) error("Enter email and password")
        if ('@' !in emailValue) {
            error("Immich logs in with email, not your name. Use the same email as the Immich app.")
        }
        ping(base)
        val body = JSONObject()
            .put("email", emailValue)
            .put("password", passwordValue)
        val req = Request.Builder()
            .url("$base/auth/login")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(errorMessage(resp.code, text))
            val json = JSONObject(text)
            val token = json.optString("accessToken")
            if (token.isBlank()) error("Login did not return a token")
            prefs.saveLogin(
                url = url,
                email = json.optString("userEmail").ifBlank { emailValue },
                token = token,
                user = json.optString("name").ifBlank { json.optString("userEmail") },
            )
            return ImmichUser(
                name = json.optString("name").ifBlank { json.optString("userEmail") },
                email = json.optString("userEmail").ifBlank { emailValue },
            )
        }
    }

    fun listAssets(page: Int = 1, size: Int = 80): ImmichAssetPage {
        val body = JSONObject()
            .put("page", page)
            .put("size", size)
            .put("withExif", false)
        val json = post("/search/metadata", body)
        val assets = json.optJSONObject("assets")
        val items = assets?.optJSONArray("items") ?: JSONArray()
        val base = prefs.apiBase
        val result = ArrayList<ImmichAsset>(items.length())
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val id = o.getString("id")
            val type = o.optString("type")
            val created = parseTime(o.optString("localDateTime").ifBlank { o.optString("fileCreatedAt") })
            result += ImmichAsset(
                id = id,
                fileName = o.optString("originalFileName").ifBlank { "photo" },
                isVideo = type.equals("VIDEO", ignoreCase = true),
                createdAt = created,
                thumbUrl = "$base/assets/$id/thumbnail?size=thumbnail",
                previewUrl = "$base/assets/$id/thumbnail?size=preview",
                originalUrl = "$base/assets/$id/original",
            )
        }
        return ImmichAssetPage(result, nextPage(assets, page, result.size, size))
    }

    private fun nextPage(assets: JSONObject?, page: Int, count: Int, size: Int): Int? {
        val raw = assets?.opt("nextPage")
        val parsed = when (raw) {
            null, JSONObject.NULL, false -> null
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
        if (parsed != null && parsed > page) return parsed
        return if (count >= size) page + 1 else null
    }

    fun download(url: String, dest: java.io.File) {
        val (header, value) = prefs.imageAuthHeader
        val req = Request.Builder().url(url).addHeader(header, value).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Could not download (${resp.code})")
            dest.outputStream().use { out ->
                resp.body?.byteStream()?.copyTo(out) ?: error("Empty download")
            }
        }
    }

    fun existingDeviceAssets(ids: List<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val body = JSONObject()
            .put("deviceAssetIds", JSONArray(ids))
            .put("deviceId", prefs.deviceId)
        val wanted = ids.toSet()
        for (path in listOf("/assets/exist", "/asset/exist")) {
            val text = runCatching { postRaw(path, body) }.getOrNull() ?: continue
            val found = parseExistingIds(text, wanted)
            if (found.isNotEmpty() || text.isNotBlank()) return found
        }
        return emptySet()
    }

    fun alreadyOnServerByChecksum(
        resolver: ContentResolver,
        items: List<MediaItem>,
        knownChecksums: Set<String>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Pair<Set<String>, Set<String>> {
        if (items.isEmpty()) return emptySet<String>() to emptySet()
        val known = knownChecksums.map { it.lowercase() }.toSet()
        val skippedIds = mutableSetOf<String>()
        val newChecksums = mutableSetOf<String>()
        val toCheck = ArrayList<Pair<String, String>>(items.size)
        items.forEachIndexed { index, item ->
            onProgress(index + 1, items.size)
            val sum = runCatching { sha1Hex(resolver, item.uri) }.getOrNull() ?: return@forEachIndexed
            if (sum in known) {
                skippedIds += item.deviceAssetId
                newChecksums += sum
            } else {
                toCheck += item.deviceAssetId to sum
            }
        }
        toCheck.chunked(64).forEach { chunk ->
            val rejected = runCatching { bulkUploadCheck(chunk) }.getOrDefault(emptySet())
            skippedIds += rejected
            chunk.forEach { (id, sum) ->
                if (id in rejected) newChecksums += sum
            }
        }
        return skippedIds to newChecksums
    }

    fun sha1Hex(resolver: ContentResolver, uri: android.net.Uri): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        resolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        } ?: error("Could not read file")
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun bulkUploadCheck(assets: List<Pair<String, String>>): Set<String> {
        if (assets.isEmpty()) return emptySet()
        val arr = JSONArray()
        assets.forEach { (id, checksum) ->
            arr.put(JSONObject().put("id", id).put("checksum", checksum))
        }
        val body = JSONObject().put("assets", arr)
        val text = runCatching { postRaw("/assets/bulk-upload-check", body) }
            .recoverCatching { postRaw("/asset/bulk-upload-check", body) }
            .getOrElse { return emptySet() }
        val found = mutableSetOf<String>()
        val json = JSONObject(text.ifBlank { "{}" })
        json.optJSONArray("results")?.let { results ->
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val action = o.optString("action")
                val reason = o.optString("reason")
                if (action.equals("reject", true) || reason.equals("duplicate", true)) {
                    found += o.optString("id")
                }
            }
        }
        return found
    }

    private fun parseExistingIds(text: String, wanted: Set<String>): Set<String> {
        val found = mutableSetOf<String>()
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                when (val value = arr.get(i)) {
                    is String -> if (value in wanted) found += value
                    is JSONObject -> {
                        val id = value.optString("deviceAssetId").ifBlank { value.optString("id") }
                        val exists = value.optBoolean("exists", value.optBoolean("isExisting", id in wanted))
                        if (exists && id in wanted) found += id
                    }
                }
            }
            return found
        }
        val json = JSONObject(trimmed.ifBlank { "{}" })
        json.optJSONArray("existingIds")?.let { arr ->
            for (i in 0 until arr.length()) {
                val id = arr.optString(i)
                if (id in wanted) found += id
            }
        }
        json.optJSONArray("results")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("deviceAssetId").ifBlank { o.optString("id") }
                if ((o.optBoolean("exists") || o.optBoolean("isExisting")) && id in wanted) found += id
            }
        }
        return found
    }

    private fun postRaw(path: String, json: JSONObject): String {
        val req = authed()
            .url("${prefs.apiBase}$path")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(errorMessage(resp.code, text))
            return text
        }
    }

    fun upload(resolver: ContentResolver, item: MediaItem): String {
        val created = Instant.ofEpochMilli(item.dateTaken.coerceAtLeast(0L)).toString()
        val mime = item.mimeType.ifBlank { "application/octet-stream" }.toMediaTypeOrNull()
        val fileBody = object : RequestBody() {
            override fun contentType() = mime
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(item.uri)?.use { input ->
                    sink.writeAll(input.source())
                } ?: throw IOException("Could not read ${item.displayName}")
            }
        }
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("deviceAssetId", item.deviceAssetId)
            .addFormDataPart("deviceId", prefs.deviceId)
            .addFormDataPart("fileCreatedAt", created)
            .addFormDataPart("fileModifiedAt", created)
            .addFormDataPart("filename", item.displayName)
            .addFormDataPart("isFavorite", "false")
            .addFormDataPart("assetData", item.displayName, fileBody)
            .build()
        val req = authed()
            .url("${prefs.apiBase}/assets")
            .post(multipart)
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(errorMessage(resp.code, text))
            val json = JSONObject(text.ifBlank { "{}" })
            return json.optString("status").ifBlank { "created" }
        }
    }

    private fun ping(base: String) {
        val paths = listOf("/server/ping", "/server-info/ping")
        var last: Exception? = null
        for (path in paths) {
            try {
                val req = Request.Builder().url("$base$path").get().build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) return
                    last = IOException(errorMessage(resp.code, resp.body?.string().orEmpty()))
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Could not reach Immich — check the server address")
    }

    private fun post(path: String, json: JSONObject): JSONObject {
        val req = authed()
            .url("${prefs.apiBase}$path")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(errorMessage(resp.code, text))
            return JSONObject(text.ifBlank { "{}" })
        }
    }

    private fun authed(): Request.Builder {
        val builder = Request.Builder().addHeader("Accept", "application/json")
        if (prefs.accessToken.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${prefs.accessToken}")
        } else if (prefs.apiKey.isNotBlank()) {
            builder.addHeader("x-api-key", prefs.apiKey)
        }
        return builder
    }

    private fun parseTime(raw: String): Long = runCatching {
        Instant.parse(raw.replace(" ", "T").let { if (it.endsWith("Z") || it.contains("+")) it else it + "Z" })
            .toEpochMilli()
    }.getOrDefault(0L)

    private fun errorMessage(code: Int, body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val fromErrors = json?.optJSONArray("errors")?.let { errors ->
            buildList {
                for (i in 0 until errors.length()) {
                    val e = errors.optJSONObject(i) ?: continue
                    val path = e.optJSONArray("path")?.optString(0).orEmpty().ifBlank { e.optString("path") }
                    val msg = e.optString("message")
                    add(
                        when {
                            path.contains("email", ignoreCase = true) ||
                                msg.contains("email", ignoreCase = true) ->
                                "Email isn’t valid — use the email from the Immich app, not your name"
                            path.contains("password", ignoreCase = true) ->
                                "Password is required"
                            msg.isNotBlank() -> msg
                            else -> path
                        },
                    )
                }
            }.filter { it.isNotBlank() }.distinct().joinToString("\n")
        }.orEmpty()
        if (fromErrors.isNotBlank()) return fromErrors

        val fromJson = when (val msg = json?.opt("message")) {
            is String -> if (msg.equals("Validation failed", ignoreCase = true)) {
                "Immich needs a real email (you@example.com), not a name"
            } else {
                msg
            }
            is JSONArray -> (0 until msg.length()).joinToString(" ") { msg.optString(it) }
            else -> json?.optString("error").orEmpty()
        }
        return when {
            fromJson.isNotBlank() -> fromJson
            code == 401 || code == 403 -> "Wrong email or password"
            code == 404 -> "Immich API not found — check the server address"
            else -> "Immich error $code"
        }
    }
}
