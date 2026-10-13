package com.infraviored.glimpse.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.Folder
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.Section
import com.infraviored.glimpse.ui.components.PhotoGrid
import com.infraviored.glimpse.ui.components.Selection
import com.infraviored.glimpse.ui.components.SelectionScaffold
import com.infraviored.glimpse.ui.loadInto
import com.infraviored.glimpse.ui.theme.glimpse

class FolderVm(private val api: Api, val path: String) : ViewModel() {
    var folder by mutableStateOf<Folder?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        error = null
        viewModelScope.loadInto({ error = it }, { api.folder(path) }) { folder = it }
    }
}

/** A folder as a gallery: its sub folders first, then its pictures. */
@Composable
fun FolderScreen(path: String, asTab: Boolean = false, bottomPadding: Dp = 0.dp) {
    val app = LocalApp.current
    val vm: FolderVm = viewModel(key = "folder-$path") { FolderVm(app.api, path) }
    val folder = vm.folder
    val items = folder?.items.orEmpty().filterNot { it.fileId in app.deleted }
    val sections = remember(items) { if (items.isEmpty()) emptyList() else listOf(Section("all", "", items)) }
    val selection = remember { Selection() }
    val gridState = rememberLazyGridState()
    // Files moved in or out, the private area locked or unlocked: load again
    val version = app.version
    androidx.compose.runtime.LaunchedEffect(version) { if (version > 0) vm.load() }
    val title = if (path == "/" || path.isEmpty()) stringResource(R.string.tab_folders) else path.substringAfterLast('/')

    val body: @Composable (Dp) -> Unit = { bottom ->
        PhotoGrid(
            sections = sections,
            selection = selection,
            onOpen = { app.open(items, it) },
            state = gridState,
            loading = folder == null && vm.error == null,
            error = vm.error,
            onRetry = vm::load,
            contentPadding = PaddingValues(bottom = bottom),
            columnsKey = "folder",
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }, contentType = "top") {
                Column(Modifier.statusBarsPadding()) {
                    if (!asTab) {
                        IconButton(onClick = app.back, modifier = Modifier.padding(4.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = glimpse.text)
                        }
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.displaySmall,
                        color = glimpse.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (asTab) 8.dp else 0.dp, bottom = 4.dp),
                    )
                    if (!asTab) {
                        Text(path, style = MaterialTheme.typography.bodySmall, color = glimpse.muted, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    if (folder?.isPrivate == true) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null, tint = glimpse.muted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.folder_private), color = glimpse.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    folder?.folders?.forEach { sub ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { app.folder(sub.path) }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(glimpse.raised),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Folder, null, tint = glimpse.accentText, modifier = Modifier.size(22.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Text(sub.name, style = MaterialTheme.typography.titleMedium, color = glimpse.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (folder != null && folder.folders.isEmpty() && items.isEmpty()) {
                        Text(stringResource(R.string.folder_empty), color = glimpse.muted, modifier = Modifier.padding(16.dp))
                    }
                    if (folder?.truncated == true) {
                        Text(stringResource(R.string.folder_truncated), color = glimpse.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    }
                    if (items.isNotEmpty()) Spacer(Modifier.size(12.dp))
                }
            }
        }
    }

    if (asTab) body(bottomPadding) else SelectionScaffold(selection, items) { body(it) }
}
