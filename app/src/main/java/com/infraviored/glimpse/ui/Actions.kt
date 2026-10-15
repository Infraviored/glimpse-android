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

    /** Pictures for which a folder is being picked, and whether they are copied there. */
    var transferFor by mutableStateOf<List<Item>?>(null)
    var transferCopy by mutableStateOf(false)
    var albumFor by mutableStateOf<List<Item>?>(null)
    var tagFor by mutableStateOf<List<Item>?>(null)

    fun pickFolder(items: List<Item>, copy: Boolean) {
        if (items.isEmpty()) return
        transferCopy = copy
        transferFor = items
    }

    fun recentFolders(): List<String> =
        context.getSharedPreferences("actions", Context.MODE_PRIVATE).getString("folders", "").orEmpty().split('\n').filter { it.isNotBlank() }

    private fun rememberFolder(path: String) {
        context.getSharedPreferences("actions", Context.MODE_PRIVATE).edit()
            .putString("folders", com.infraviored.glimpse.data.Paths.recent(recentFolders(), path).joinToString("\n")).apply()
    }

    /** Move or copy into [target]; a move can be undone from the notice. */
    fun transfer(items: List<Item>, target: String, copy: Boolean, onDone: () -> Unit = {}) {
        if (items.isEmpty() || busy) return
        busy = true
        scope.launch {
            try {
                val result = api.transfer(items, target, copy)
                rememberFolder(target)
                transferFor = null
                onDone()
                val folder = com.infraviored.glimpse.data.Paths.name(target).ifEmpty { context.getString(R.string.folder_root) }
                val text = when {
                    result.failed.isNotEmpty() -> context.resources.getQuantityString(R.plurals.transfer_failed, result.failed.size, result.failed.size, result.done)
                    copy -> context.resources.getQuantityString(R.plurals.copied_to, result.done, result.done, folder)
                    else -> context.resources.getQuantityString(R.plurals.moved_to, result.done, result.done, folder)
                }
                if (!copy && result.moved.isNotEmpty()) {
                    app.notices.show(text, context.getString(R.string.undo)) { undo(result.moved) }
                } else {
                    app.notices.show(text)
                }
                app.changed()
            } catch (e: Exception) {
                app.notices.show(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }
    }

    private fun undo(moves: List<com.infraviored.glimpse.data.Moved>) {
        scope.launch {
            try {
                val result = api.undoMove(moves)
                val text = if (result.failed.isNotEmpty()) {
                    context.resources.getQuantityString(R.plurals.transfer_failed, result.failed.size, result.failed.size, result.done)
                } else {
                    context.resources.getQuantityString(R.plurals.moved_back, result.done, result.done)
                }
                app.notices.show(text)
                app.changed()
            } catch (e: Exception) {
                app.notices.show(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /**
     * Sensitive or not, by hand, with undo. The tiles change at once; the pictures stay in
     * the list until it loads again (going through a list stays calm that way).
     */
    fun markSensitive(items: List<Item>, on: Boolean, onDone: () -> Unit = {}) {
        if (items.isEmpty()) return
        scope.launch {
            try {
                val res = api.markSensitive(items.associate { it.fileId.toString() to on })
                app.sensitiveMarks = app.sensitiveMarks + res.sensitive.mapKeys { it.key.toLong() }
                onDone()
                val text = context.resources.getQuantityString(if (on) R.plurals.marked_sensitive else R.plurals.marked_not_sensitive, items.size, items.size)
                app.notices.show(text, context.getString(R.string.undo)) {
                    scope.launch {
                        runCatching { api.markSensitive(res.previous) }
                            .onSuccess { back -> app.sensitiveMarks = app.sensitiveMarks + back.sensitive.mapKeys { it.key.toLong() } }
                            .onFailure { app.notices.show(context.getString(R.string.action_failed, it.message ?: it.javaClass.simpleName)) }
                    }
                }
            } catch (e: Exception) {
                app.notices.show(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /**
     * "Sensitive up to here" on one result of a text search: the search counts down to it.
     * The results above it get the mark at once; the lists load again for the rest.
     */
    fun sensitiveUpToHere(q: String, item: Item, items: List<Item>, onDone: () -> Unit = {}) {
        val assetId = item.thumbId ?: item.id ?: return
        scope.launch {
            try {
                val status = api.setSensitiveTerm(q, assetId)
                val upTo = items.indexOfFirst { it.fileId == item.fileId }
                app.sensitiveMarks = app.sensitiveMarks + items.take(upTo + 1).associate { it.fileId to true }
                onDone()
                val count = status.terms.firstOrNull { it.q.equals(q, ignoreCase = true) }?.count ?: (upTo + 1)
                app.notices.show(context.resources.getQuantityString(R.plurals.sensitive_term_set, count, count, q))
            } catch (e: Exception) {
                app.notices.show(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /** Private or not; private pictures vanish while the area is locked. */
    fun setPrivate(items: List<Item>, on: Boolean, onDone: () -> Unit = {}) {
        if (items.isEmpty()) return
        scope.launch {
            try {
                api.setPrivate(items.map { it.fileId }, on)
                app.loadPrivate()
                app.changed()
                onDone()
                val locked = app.privateArea?.unlocked != true
                if (on && locked) app.hidePrivate(items.map { it.fileId })
                app.notices.show(
                    context.resources.getQuantityString(
                        if (!on) R.plurals.made_public else if (locked) R.plurals.made_private_hidden else R.plurals.made_private,
                        items.size, items.size,
                    ),
                )
            } catch (e: com.infraviored.glimpse.data.ApiException) {
                app.notices.show(if (e.code == 403) context.getString(R.string.private_unlock_first) else context.getString(R.string.action_failed, e.message))
            } catch (e: Exception) {
                app.notices.show(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun addToAlbum(items: List<Item>, album: String, create: Boolean, onDone: () -> Unit = {}) {
        if (items.isEmpty() || busy) return
        busy = true
        scope.launch {
            try {
                if (create) api.createAlbum(album)
                val added = api.addToAlbum(album, items)
                albumFor = null
                onDone()
                app.notices.show(context.resources.getQuantityString(R.plurals.added_to_album, added, added, album))
            } catch (e: Exception) {
                app.notices.show(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }
    }

    fun tag(items: List<Item>, tagIds: List<Long>, newTags: List<String>, onDone: () -> Unit = {}) {
        if (items.isEmpty() || busy) return
        busy = true
        scope.launch {
            try {
                val result = api.tag(items.map { it.fileId }, tagIds, newTags)
                tagFor = null
                onDone()
                app.notices.show(context.resources.getQuantityString(R.plurals.tagged, result.done, result.done))
            } catch (e: Exception) {
                app.notices.show(context.getString(R.string.action_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }
    }
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
