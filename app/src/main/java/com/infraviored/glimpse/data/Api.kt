package com.infraviored.glimpse.data

import android.net.Uri
import com.infraviored.glimpse.Account
import com.infraviored.glimpse.Nextcloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The server said something the app can show as is. */
class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Glimpse's own API (the same one its web page uses), with the app password.
 *
 * Every request to the user's Nextcloud carries the app password. Should it stop working
 * (revoked on the server), the first 401 trips [authLost] and no further request is sent:
 * a grid of a hundred thumbnails would otherwise count as a hundred failed logins and get
 * the user's address throttled.
 */
class Api(val account: Account) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val server: HttpUrl = account.server.toHttpUrl()

    private val _authLost = MutableStateFlow(false)
    val authLost: StateFlow<Boolean> = _authLost.asStateFlow()

    /** The Nextcloud user id (WebDAV paths use it; the login name may be an email address). */
    @Volatile
    var userId: String = account.user
        private set

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .cookieJar(SessionJar())
        .addInterceptor(AuthInterceptor())
        .build()

    private fun isOwn(url: HttpUrl) = url.host == server.host && url.port == server.port && url.scheme == server.scheme

    private inner class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!isOwn(request.url)) return chain.proceed(request)
            if (_authLost.value) throw ApiException(401, "Not logged in")
            val response = chain.proceed(
                request.newBuilder()
                    .header("Authorization", account.basicAuth)
                    .header("OCS-APIRequest", "true")
                    .header("User-Agent", Nextcloud.userAgent)
                    .build(),
            )
            if (response.code == 401) _authLost.value = true
            return response
        }
    }

    /** Keeps Nextcloud's session for the app's lifetime, so it is not started anew for every thumbnail. */
    private class SessionJar : CookieJar {
        private val cookies = mutableMapOf<String, Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { this.cookies[it.name] = it }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.filter { it.matches(url) }
    }

    // ---- addresses -------------------------------------------------------------------------

    private fun appUrl(path: String) = "${account.server}/index.php/apps/glimpse$path"

    /** Grid size: Immich's small thumbnail; for files Immich has not indexed, Nextcloud's preview. */
    fun thumbnailUrl(item: Item): String =
        (item.thumbId ?: item.id)?.let { appUrl("/api/assets/${enc(it)}/thumbnail") }
            ?: "${account.server}/index.php/core/preview?fileId=${item.fileId}&x=256&y=256&a=1&mimeFallback=true"

    /** Screen size (about 1440 px). */
    fun previewUrl(item: Item): String =
        (item.thumbId ?: item.id)?.let { appUrl("/api/assets/${enc(it)}/thumbnail?size=preview") }
            ?: "${account.server}/index.php/core/preview?fileId=${item.fileId}&x=2048&y=2048&a=1&mimeFallback=true"

    fun faceUrl(personId: String): String = appUrl("/api/people/${enc(personId)}/face")

    /**
     * Where files moved in this session went: lists loaded before still have the old path,
     * and downloading, sharing or adding them to an album needs the current one.
     */
    private val movedTo = java.util.concurrent.ConcurrentHashMap<Long, String>()

    fun pathOf(item: Item): String = movedTo[item.fileId] ?: item.path

    /** The original file. */
    fun fileUrl(item: Item): String {
        val path = pathOf(item)
        if (path.isEmpty() || path == "/") throw ApiException(404, "No file path")
        val normalized = if (path.startsWith("/")) path else "/$path"
        return "${account.server}/remote.php/dav/files/${enc(userId)}" + normalized.split('/').joinToString("/") { enc(it) }
    }

    private fun enc(s: String) = Uri.encode(s)

    // ---- calls -------------------------------------------------------------------------------

    suspend fun loadUser() {
        val body = get("${account.server}/ocs/v2.php/cloud/user?format=json")
        json.parseToJsonElement(body).jsonObject["ocs"]?.jsonObject?.get("data")?.jsonObject?.get("id")
            ?.jsonPrimitive?.content?.let { userId = it }
    }

    /**
     * Back in time from [before] (newest first), or forward from [after] to today (oldest first).
     * With [person]: only the pictures that person is in; page 1 then lists that person's years.
     */
    suspend fun timeline(page: Int, before: String?, after: String? = null, person: String? = null, seen: Int = 0): Page =
        decode(get(appUrl("/api/timeline") + query("page" to page, "before" to before, "after" to after, "person" to person, "seen" to seen)))

    suspend fun memories(date: String): List<Memory> = decode(get(appUrl("/api/memories") + query("date" to date)))

    suspend fun search(
        q: String = "",
        page: Int = 1,
        person: String = "",
        type: String = "",
        similar: String = "",
        tag: String = "",
        seen: Int = 0,
    ): Page = decode(
        get(appUrl("/api/search") + query("q" to q, "page" to page, "person" to person, "type" to type, "similar" to similar, "tag" to tag, "seen" to seen)),
    )

    suspend fun people(): People = decode(get(appUrl("/api/people")))

    suspend fun folder(path: String): Folder = decode(get(appUrl("/api/folder") + query("path" to path)))

    suspend fun faces(assetId: String): Faces = decode(get(appUrl("/api/assets/${enc(assetId)}/faces")))

    suspend fun tags(): List<Tag> = decode(get(appUrl("/api/tags") + query("visible" to 1)))

    suspend fun favorite(fileIds: List<Long>, on: Boolean) {
        post(appUrl("/api/files/favorite"), buildJsonObject { putJsonArray("fileIds") { fileIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }; put("on", on) })
    }

    suspend fun delete(fileIds: List<Long>): Done =
        decode(post(appUrl("/api/files/delete"), buildJsonObject { putJsonArray("fileIds") { fileIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } } }))

    suspend fun renamePerson(id: String, name: String) {
        send("PUT", appUrl("/api/people/${enc(id)}"), buildJsonObject { put("name", name) })
    }

    // ---- people --------------------------------------------------------------------------

    /** Name, hidden and favorite of a person; only what is not null changes. */
    suspend fun updatePerson(id: String, name: String? = null, hidden: Boolean? = null, favorite: Boolean? = null): PersonUpdate =
        decode(send("PUT", appUrl("/api/people/${enc(id)}"), Requests.updatePerson(name, hidden, favorite)))

    /** [others] become [target], with all their pictures. Cannot be undone. */
    suspend fun mergePeople(target: String, others: List<String>) {
        post(appUrl("/api/people/${enc(target)}/merge"), Requests.merge(others))
    }

    /** "Not this person": the faces of [personId] in [assetIds] go to [target]. */
    suspend fun moveFaces(personId: String, assetIds: List<String>, target: FaceTarget): MovedFaces =
        decode(post(appUrl("/api/people/${enc(personId)}/move-faces"), Requests.moveFaces(assetIds, target)))

    // ---- files -------------------------------------------------------------------------------

    /** Move or copy into [target] (a folder path in the user's files). */
    suspend fun transfer(items: List<Item>, target: String, copy: Boolean): Transfer {
        val result: Transfer = decode(post(appUrl("/api/files/transfer"), Requests.transfer(items.map { it.fileId }, target, copy)))
        val byId = items.associateBy { it.fileId }
        result.moved.forEach { m -> byId[m.fileId]?.let { movedTo[m.fileId] = Paths.join(target, pathOf(it).substringAfterLast('/')) } }
        return result
    }

    /** Moves back to where [Transfer.moved] says they came from. */
    suspend fun undoMove(moves: List<Moved>): Done {
        val result: Done = decode(post(appUrl("/api/files/undo"), Requests.undo(moves)))
        val failedIds = result.failed.map { it.fileId }.toSet()
        moves.filter { it.fileId !in failedIds }.forEach { m ->
            movedTo[m.fileId] = Paths.join(m.from, m.fromName ?: movedTo[m.fileId]?.substringAfterLast('/').orEmpty())
        }
        return result
    }

    /** Tags the user may assign (not every tag they can see). */
    suspend fun assignableTags(): List<Tag> = decode(get(appUrl("/api/tags")))

    suspend fun tag(fileIds: List<Long>, tagIds: List<Long>, newTags: List<String>): Done =
        decode(post(appUrl("/api/files/tag"), Requests.tag(fileIds, tagIds, newTags)))

    // ---- albums (Nextcloud Photos, over WebDAV) ---------------------------------------------

    private fun albumsUrl() = "${account.server}/remote.php/dav/photos/${enc(userId)}/albums/"

    suspend fun albums(): List<String> {
        val body = call(
            Request.Builder().url(albumsUrl()).header("Depth", "1")
                .method("PROPFIND", Dav.PROPFIND_NAME.toRequestBody("application/xml".toMediaType())).build(),
        )
        return Dav.albumNames(body)
    }

    suspend fun createAlbum(name: String) {
        call(Request.Builder().url(albumsUrl() + enc(name)).method("MKCOL", null).build())
    }

    /** How many were added; ones already in the album are skipped. */
    suspend fun addToAlbum(name: String, items: List<Item>): Int = withContext(Dispatchers.IO) {
        var added = 0
        for (item in items) {
            val request = Request.Builder().url(fileUrl(item)).method("COPY", null)
                .header("Destination", albumsUrl() + enc(name) + "/" + enc(pathOf(item).substringAfterLast('/')))
                .header("Overwrite", "F")
                .build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    added++
                } else if (response.code != 412) {
                    val body = response.body?.string().orEmpty()
                    val message = try {
                        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
                    } catch (e: Exception) {
                        null
                    }
                    throw ApiException(response.code, message ?: "HTTP ${response.code}")
                }
            }
        }
        added
    }

    // ---- private area ----------------------------------------------------------------------

    suspend fun privateState(): PrivateState = decode(get(appUrl("/api/private")))

    /**
     * Unlock for this session. With [password], Nextcloud's own password confirmation comes
     * first (the session then counts as confirmed for half an hour).
     */
    suspend fun unlockPrivate(password: String?) {
        if (password != null) {
            call(
                Request.Builder().url("${account.server}/index.php/login/confirm").header("Accept", "application/json")
                    .post(buildJsonObject { put("password", password) }.toString().toRequestBody("application/json".toMediaType())).build(),
            )
            post(appUrl("/api/private/unlock-password"), buildJsonObject { })
        } else {
            post(appUrl("/api/private/unlock"), buildJsonObject { })
        }
    }

    suspend fun lockPrivate() {
        post(appUrl("/api/private/lock"), buildJsonObject { })
    }

    /** Marking private works while locked; taking it back needs the area unlocked (403 "locked"). */
    suspend fun setPrivate(fileIds: List<Long>, on: Boolean) {
        post(appUrl("/api/private/files"), Requests.privateFiles(fileIds, on))
    }

    // ---- sensitive pictures ------------------------------------------------------------------

    suspend fun sensitiveStatus(): SensitiveStatus = decode(get(appUrl("/api/sensitive")))

    suspend fun sensitivePrefs(): SensitivePrefs = decode(get(appUrl("/api/prefs")))

    /** Save some of the user's sensitive settings (keys as on the server: sensitive_mode, ...). */
    suspend fun saveSensitive(changes: Map<String, Any>): SensitivePrefs =
        decode(send("PUT", appUrl("/api/prefs"), Requests.prefs(changes)))

    suspend fun markSensitive(marks: Map<String, Boolean?>): SensitiveMarks =
        decode(post(appUrl("/api/sensitive/marks"), Requests.sensitiveMarks(marks)))

    // ---- map, duplicates ---------------------------------------------------------------------

    suspend fun mapMarkers(): MapData {
        val body = get(appUrl("/api/map"))
        return withContext(Dispatchers.Default) { MapData.parse(json.parseToJsonElement(body)) }
    }

    /** A map tile through Glimpse's proxy (the app never asks OpenStreetMap directly). */
    fun tileUrl(z: Int, x: Int, y: Int): String = appUrl("/api/tiles/$z/$x/$y")

    suspend fun duplicates(): List<DuplicateGroup> = decode(get(appUrl("/api/duplicates")))

    /** "Not duplicates": the group is gone for everyone. */
    suspend fun dismissDuplicate(id: String) {
        call(Request.Builder().url(appUrl("/api/duplicates/${enc(id)}")).header("Accept", "application/json").delete().build())
    }

    // ---- plumbing ----------------------------------------------------------------------------

    private inline fun <reified T> decode(body: String): T = json.decodeFromString(body)

    private fun query(vararg params: Pair<String, Any?>): String =
        params.filter { it.second != null && it.second.toString().isNotEmpty() }
            .joinToString("&", prefix = "?") { (k, v) -> enc(k) + "=" + enc(v.toString()) }

    private suspend fun get(url: String): String = call(Request.Builder().url(url).header("Accept", "application/json").build())

    private suspend fun post(url: String, body: JsonObject): String = send("POST", url, body)

    private suspend fun send(method: String, url: String, body: JsonObject): String =
        call(
            Request.Builder().url(url).header("Accept", "application/json")
                .method(method, body.toString().toRequestBody("application/json".toMediaType())).build(),
        )

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try {
                    json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
                } catch (e: Exception) {
                    null
                }
                throw ApiException(response.code, message ?: "HTTP ${response.code}")
            }
            body
        }
    }
}
