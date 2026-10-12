package com.infraviored.glimpse

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * window.GlimpseApp in the page, only for pages of the user's own Nextcloud (a web message
 * listener with that origin, unlike addJavascriptInterface which every frame would see).
 *
 * The WebView cannot download the answer of a form POST (Glimpse's "Download as ZIP"), so
 * the page posts {"type": "postDownload", "url": …, "fields": {…}} instead; the app sends the
 * form with the WebView's session and saves the answer in Downloads.
 */
class AppBridge(private val context: Context, private val server: String) {

    /** The origin pages must come from, e.g. "https://cloud.example.com". */
    val origin: String = Uri.parse(server).let { u -> "${u.scheme}://${u.encodedAuthority}" }

    fun onMessage(data: String?) {
        val message = try {
            JSONObject(data ?: return)
        } catch (e: Exception) {
            return
        }
        when (message.optString("type")) {
            "postDownload" -> postDownload(message.optString("url"), message.optJSONObject("fields") ?: JSONObject())
        }
    }

    private fun postDownload(url: String, fields: JSONObject) {
        if (!sameServer(url)) return
        toast(context.getString(R.string.download_started))
        thread(name = "glimpse-download") {
            try {
                val name = post(url, fields)
                toast(context.getString(R.string.download_done, name))
            } catch (e: Exception) {
                toast(context.getString(R.string.download_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /** Absolute address on this Nextcloud; the page may also send one relative to the origin. */
    private fun sameServer(url: String): Boolean = url.startsWith("$origin/") && !url.contains("@")

    private fun post(url: String, fields: JSONObject): String {
        val body = fields.keys().asSequence().joinToString("&") { key ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(fields.optString(key), "UTF-8")
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 120000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", Nextcloud.userAgent)
            CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code != 200) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = try {
                    JSONObject(error).optString("error")
                } catch (e: Exception) {
                    ""
                }
                throw IllegalStateException(message.ifEmpty { "HTTP $code" })
            }
            val mime = conn.contentType?.substringBefore(';') ?: "application/zip"
            val name = URLUtil.guessFileName(url, conn.getHeaderField("Content-Disposition"), mime)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val item = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("no Downloads folder")
            try {
                resolver.openOutputStream(item)!!.use { out -> conn.inputStream.use { it.copyTo(out) } }
            } catch (e: Exception) {
                resolver.delete(item, null, null)
                throw e
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(item, values, null, null)
            return name
        } finally {
            conn.disconnect()
        }
    }

    private fun toast(text: String) {
        android.os.Handler(context.mainLooper).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }
}
