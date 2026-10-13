package com.infraviored.glimpse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.FolderRef
import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.data.Paths
import com.infraviored.glimpse.data.Tag
import com.infraviored.glimpse.ui.components.GlimpseSheet
import com.infraviored.glimpse.ui.components.PillField
import com.infraviored.glimpse.ui.components.PrimaryButton
import com.infraviored.glimpse.ui.components.SheetButtons
import com.infraviored.glimpse.ui.components.SheetRow
import com.infraviored.glimpse.ui.components.SheetTitle
import com.infraviored.glimpse.ui.search.Chip
import com.infraviored.glimpse.ui.theme.glimpse

/** Every question an action can ask: delete, which folder, which album, which tags. */
@Composable
fun ActionSheets(actions: PhotoActions, onDone: () -> Unit) {
    DeleteDialog(actions, onDone)
    actions.transferFor?.let { items -> FolderPickerSheet(actions, items, actions.transferCopy, onDone) }
    actions.albumFor?.let { items -> AlbumSheet(actions, items, onDone) }
    actions.tagFor?.let { items -> TagSheet(actions, items, onDone) }
}

/** Walk the folders down from the top, then move or copy into the one shown. */
@Composable
private fun FolderPickerSheet(actions: PhotoActions, items: List<Item>, copy: Boolean, onDone: () -> Unit) {
    val app = LocalApp.current
    var path by remember { mutableStateOf("/") }
    var folders by remember { mutableStateOf<List<FolderRef>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val recent = remember { actions.recentFolders() }
    LaunchedEffect(path) {
        folders = null
        error = null
        try {
            folders = app.api.folder(path).folders
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    GlimpseSheet(onDismiss = { actions.transferFor = null }) {
        SheetTitle(pluralStringResource(if (copy) R.plurals.copy_title else R.plurals.move_title, items.size, items.size))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (path != "/") {
                IconButton(onClick = { path = Paths.parent(path) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = glimpse.text)
                }
            }
            Column(Modifier.weight(1f).padding(start = if (path == "/") 4.dp else 0.dp)) {
                Text(
                    Paths.name(path).ifEmpty { stringResource(R.string.folder_root) },
                    style = MaterialTheme.typography.titleLarge,
                    color = glimpse.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (path != "/") Text(path, style = MaterialTheme.typography.bodySmall, color = glimpse.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp).verticalScroll(rememberScrollState())) {
            if (path == "/" && recent.isNotEmpty()) {
                recent.forEach { r ->
                    SheetRow(Icons.Outlined.History, Paths.name(r).ifEmpty { stringResource(R.string.folder_root) }, r) { path = r }
                }
                Spacer(Modifier.height(8.dp))
            }
            val list = folders
            when {
                error != null -> Text(stringResource(R.string.load_failed, error!!), color = glimpse.muted, modifier = Modifier.padding(8.dp))
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = glimpse.muted)
                }
                list.isEmpty() -> Text(stringResource(R.string.folder_no_subfolders), color = glimpse.muted, modifier = Modifier.padding(8.dp))
                else -> list.forEach { f -> SheetRow(Icons.Outlined.Folder, f.name) { path = f.path } }
            }
        }
        SheetButtons(
            cancel = stringResource(R.string.cancel),
            confirm = stringResource(if (copy) R.string.copy_here else R.string.move_here),
            onCancel = { actions.transferFor = null },
            onConfirm = { actions.transfer(items, path, copy, onDone) },
            busy = actions.busy,
        )
    }
}

/** The albums of Nextcloud Photos, or a new one. */
@Composable
private fun AlbumSheet(actions: PhotoActions, items: List<Item>, onDone: () -> Unit) {
    val app = LocalApp.current
    var albums by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            albums = app.api.albums()
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    GlimpseSheet(onDismiss = { actions.albumFor = null }) {
        SheetTitle(pluralStringResource(R.plurals.album_title, items.size, items.size))
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            val list = albums
            when {
                error != null -> Text(stringResource(R.string.album_unavailable, error!!), color = glimpse.muted, modifier = Modifier.padding(8.dp))
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = glimpse.muted)
                }
                list.isEmpty() -> Text(stringResource(R.string.album_none), color = glimpse.muted, modifier = Modifier.padding(8.dp))
                else -> list.forEach { a -> SheetRow(Icons.Outlined.PhotoAlbum, a, enabled = !actions.busy) { actions.addToAlbum(items, a, create = false, onDone) } }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillField(name, { name = it }, stringResource(R.string.album_new), Modifier.weight(1f), leading = Icons.Outlined.Add)
            Spacer(Modifier.width(10.dp))
            PrimaryButton(
                stringResource(R.string.album_create),
                { actions.addToAlbum(items, name.trim(), create = true, onDone) },
                enabled = validAlbumName(name) && albums?.any { it.equals(name.trim(), ignoreCase = true) } != true,
                busy = actions.busy,
            )
        }
    }
}

/** Album names become a WebDAV path segment: no slashes, not empty. */
fun validAlbumName(name: String): Boolean {
    val n = name.trim()
    return n.isNotEmpty() && n != "." && n != ".." && '/' !in n && '\\' !in n
}

/** The tags the user may assign; new ones can be typed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSheet(actions: PhotoActions, items: List<Item>, onDone: () -> Unit) {
    val app = LocalApp.current
    var tags by remember { mutableStateOf<List<Tag>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var newTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            tags = app.api.assignableTags()
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    fun addTyped() {
        val n = name.trim()
        if (n.isEmpty()) return
        val existing = tags.orEmpty().firstOrNull { it.name.equals(n, ignoreCase = true) }
        if (existing != null) chosen = chosen + existing.id else if (newTags.none { it.equals(n, ignoreCase = true) }) newTags = newTags + n
        name = ""
    }
    GlimpseSheet(onDismiss = { actions.tagFor = null }) {
        SheetTitle(pluralStringResource(R.plurals.tag_title, items.size, items.size))
        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            when {
                error != null -> Text(stringResource(R.string.load_failed, error!!), color = glimpse.muted)
                tags == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = glimpse.muted)
                }
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.orEmpty().forEach { t ->
                        Chip(t.name, selected = t.id in chosen) { chosen = if (t.id in chosen) chosen - t.id else chosen + t.id }
                    }
                    newTags.forEach { n -> Chip(n, selected = true, onClear = { newTags = newTags - n }) {} }
                    if (tags.orEmpty().isEmpty() && newTags.isEmpty()) Text(stringResource(R.string.tag_none), color = glimpse.muted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PillField(name, { name = it }, stringResource(R.string.tag_new), leading = Icons.Outlined.Add, onDone = ::addTyped)
        SheetButtons(
            cancel = stringResource(R.string.cancel),
            confirm = stringResource(R.string.tag_apply),
            onCancel = { actions.tagFor = null },
            onConfirm = {
                val typed = name.trim().takeIf { it.isNotEmpty() && tags.orEmpty().none { t -> t.name.equals(it, ignoreCase = true) } }
                val typedExisting = tags.orEmpty().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id
                actions.tag(items, (chosen + listOfNotNull(typedExisting)).toList(), newTags + listOfNotNull(typed), onDone)
            },
            enabled = chosen.isNotEmpty() || newTags.isNotEmpty() || name.isNotBlank(),
            busy = actions.busy,
        )
    }
}
