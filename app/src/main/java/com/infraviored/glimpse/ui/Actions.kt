package com.infraviored.glimpse.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Share, favorite, download, delete: the same for one picture in the viewer and many in a grid. */
@Stable
class PhotoActions(
    private val context: Context,
    private val api: Api,
    private val scope: CoroutineScope,
    private val app: AppState,
) {
    /** Pictures waiting for the user to confirm deleting them. */
    var confirmDelete by mutableStateOf<List<Item>?>(null)
    var busy by mutableStateOf(false)
        private set

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    /** The originals, through Android's share sheet. */
    fun share(items: List<Item>) {
        if (items.isEmpty() || busy) return
        if (items.size > MAX_SHARE) {
            toast(context.getString(R.string.share_too_many, MAX_SHARE))
            return
        }
        busy = true
        if (items.size > 1 || items.first().isVideo) toast(context.resources.getQuantityString(R.plurals.share_preparing, items.size, items.size))
        scope.launch {
            try {
                val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
                val uris = withContext(Dispatchers.IO) {
                    items.map { item ->
                        val file = File(dir, "${item.fileId}-${item.name.replace('/', '_')}")
                        fetch(item, file)
                        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    }
                }
                val intent = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first()).setType(items.first().mime.ifEmpty { "image/*" })
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)).setType(if (items.all { !it.isVideo }) "image/*" else "*/*")
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                toast(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }
    }

    /** Into Downloads/Glimpse, with the file's own name. */
    fun download(items: List<Item>) {
        if (items.isEmpty() || busy) return
        busy = true
        toast(context.resources.getQuantityString(R.plurals.download_started, items.size, items.size))
        scope.launch {
            try {
                withContext(Dispatchers.IO) { items.forEach { save(it) } }
                toast(context.resources.getQuantityString(R.plurals.download_done, items.size, items.size))
            } catch (e: Exception) {
                toast(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }
    }

    fun favorite(items: List<Item>, on: Boolean = true) {
        if (items.isEmpty()) return
        scope.launch {
            try {
                api.favorite(items.map { it.fileId }, on)
                toast(context.resources.getQuantityString(if (on) R.plurals.favorited else R.plurals.unfavorited, items.size, items.size))
            } catch (e: Exception) {
                toast(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun delete(items: List<Item>, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                api.delete(items.map { it.fileId })
                app.deleted = app.deleted + items.map { it.fileId }
                toast(context.resources.getQuantityString(R.plurals.deleted, items.size, items.size))
                onDone()
            } catch (e: Exception) {
                toast(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun fetch(item: Item, target: File) {
        api.http.newCall(Request.Builder().url(api.fileUrl(item)).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            target.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
        }
    }

    private fun save(item: Item) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, item.name)
            put(MediaStore.Downloads.MIME_TYPE, item.mime.ifEmpty { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Glimpse")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Downloads not available")
        try {
            api.http.newCall(Request.Builder().url(api.fileUrl(item)).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                resolver.openOutputStream(uri)!!.use { out -> response.body!!.byteStream().copyTo(out) }
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    companion object {
        const val MAX_SHARE = 50
    }
}

@Composable
fun rememberPhotoActions(): PhotoActions {
    val context = LocalContext.current
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    return remember(app) { PhotoActions(context, app.api, scope, app) }
}

/** The "really delete?" question; deleted files go to the Nextcloud trash bin. */
@Composable
fun DeleteDialog(actions: PhotoActions, onDeleted: () -> Unit) {
    val items = actions.confirmDelete ?: return
    AlertDialog(
        onDismissRequest = { actions.confirmDelete = null },
        title = { Text(pluralStringResource(R.plurals.delete_title, items.size, items.size)) },
        text = { Text(stringResource(R.string.delete_text)) },
        confirmButton = {
            TextButton(onClick = {
                actions.confirmDelete = null
                actions.delete(items, onDeleted)
            }) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { TextButton(onClick = { actions.confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
    )
}
