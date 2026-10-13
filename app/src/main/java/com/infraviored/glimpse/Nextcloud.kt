package com.infraviored.glimpse

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.SSLException

/** A failure the login screen can explain; [message] is a string resource. */
class LoginException(val messageRes: Int, val detail: String? = null) : Exception(detail)

/** The few Nextcloud calls the app makes itself: everything else is the Glimpse page. */
object Nextcloud {

    /** Nextcloud names the app password after this (Settings → Security → Devices). */
    val userAgent = "Glimpse/${BuildConfig.VERSION_NAME} (Android)"

    /**
     * What the user typed, as the server's base address: "cloud.example.com",
     * "https://cloud.example.com/index.php/apps/glimpse/" and "cloud.example.com/" all give
     * "https://cloud.example.com". A Nextcloud in a sub folder keeps it.
     */
    fun normalizeServer(input: String): String? {
        var s = input.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        if (!Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(s)) s = "https://$s"
        val url = try {
            URL(s)
        } catch (e: Exception) {
            return null
        }
        if (url.protocol != "https" && url.protocol != "http") return null
        if (url.host.isNullOrEmpty()) return null
        var path = url.path.orEmpty()
        // Cut a pasted page address back to the server root
        path = path.replace(Regex("/(index\\.php)(/.*)?$"), "")
            .replace(Regex("/(apps|login|s|f)(/.*)?$"), "")
            .trimEnd('/')
        val port = if (url.port == -1) "" else ":${url.port}"
        return "${url.protocol}://${url.host.lowercase()}$port$path"
    }

    /** Reachable, really a Nextcloud, not in maintenance. */
    fun checkServer(server: String) {
        val (code, body) = try {
            request("GET", "$server/status.php")
        } catch (e: SSLException) {
            throw LoginException(R.string.error_ssl, e.message)
        } catch (e: IOException) {
            throw LoginException(R.string.error_unreachable, e.message)
        }
        val status = try {
            JSONObject(body)
        } catch (e: Exception) {
            null
        }
        if (code != 200 || status == null || !status.optBoolean("installed")) {
            throw LoginException(R.string.error_not_nextcloud)
        }
        if (status.optBoolean("maintenance")) {
            throw LoginException(R.string.error_maintenance)
        }
    }

    data class Flow(val loginUrl: String, val pollUrl: String, val token: String)

    /** Login Flow v2: the user logs in (with 2FA, SSO, …) in the browser, we poll for the result. */
    fun startLogin(server: String): Flow {
        val (code, body) = try {
            request("POST", "$server/index.php/login/v2")
        } catch (e: IOException) {
            throw LoginException(R.string.error_unreachable, e.message)
        }
        if (code != 200) throw LoginException(R.string.error_login_start, "HTTP $code")
        val json = JSONObject(body)
        val poll = json.getJSONObject("poll")
        return Flow(json.getString("login"), poll.getString("endpoint"), poll.getString("token"))
    }

    /** The account once the user granted access, null while still waiting. */
    fun poll(flow: Flow): Account? {
        val (code, body) = request("POST", flow.pollUrl, "token=" + URLEncoder.encode(flow.token, "UTF-8"))
        if (code != 200) return null
        val json = JSONObject(body)
        return Account(
            normalizeServer(json.getString("server")) ?: json.getString("server").trimEnd('/'),
            json.getString("loginName"),
            json.getString("appPassword"),
        )
    }

    /** Whether Glimpse is installed and enabled for this user. */
    fun hasGlimpse(account: Account): Boolean {
        val (code, _) = request("GET", account.glimpseUrl, auth = account.basicAuth)
        return code == 200
    }

    /** Log out for good: the app password stops working on the server too. */
    fun revoke(account: Account) {
        try {
            request("DELETE", "${account.server}/ocs/v2.php/core/apppassword", auth = account.basicAuth, ocs = true)
        } catch (e: IOException) {
            // Offline: the user can still remove it under Settings → Security.
        }
    }

    private fun request(
        method: String,
        url: String,
        form: String? = null,
        auth: String? = null,
        ocs: Boolean = false,
    ): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            auth?.let { conn.setRequestProperty("Authorization", it) }
            if (ocs) conn.setRequestProperty("OCS-APIRequest", "true")
            if (form != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(form.toByteArray()) }
            } else if (method == "POST") {
                conn.setFixedLengthStreamingMode(0)
                conn.doOutput = true
                conn.outputStream.close()
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to body
        } finally {
            conn.disconnect()
        }
    }
}
