package com.infraviored.glimpse.ui.duplicates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.DuplicateGroup
import com.infraviored.glimpse.data.DuplicateItem
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.components.GlimpseSheet
import com.infraviored.glimpse.ui.components.PrimaryButton
import com.infraviored.glimpse.ui.components.QuietButton
import com.infraviored.glimpse.ui.components.SheetButtons
import com.infraviored.glimpse.ui.components.SheetTitle
import com.infraviored.glimpse.ui.components.StatusScrim
import com.infraviored.glimpse.ui.loadInto
import com.infraviored.glimpse.ui.longDate
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.theme.Palette
import com.infraviored.glimpse.ui.theme.glimpse

class DuplicatesVm(private val api: Api) : ViewModel() {
    var groups by mutableStateOf<List<DuplicateGroup>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Per group, the file ids to keep. */
    var keep by mutableStateOf<Map<String, Set<Long>>>(emptyMap())
        private set
    var busy by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        error = null
        viewModelScope.loadInto({ error = it }, { api.duplicates() }) { list ->
            groups = list
            keep = list.associate { it.id to it.suggestedKeep() }
        }
    }

    fun toggle(group: DuplicateGroup, fileId: Long) {
        val set = keep[group.id].orEmpty()
        keep = keep + (group.id to if (fileId in set) set - fileId else set + fileId)
    }

    fun toDelete(group: DuplicateGroup): List<DuplicateItem> = group.items.filter { it.fileId !in keep[group.id].orEmpty() }

    private fun drop(group: DuplicateGroup) {
        groups = groups?.filter { it.id != group.id }
    }

    /** The others go to the trash bin. */
    fun resolve(group: DuplicateGroup, onDone: (deleted: List<Long>, done: Int, failed: Int) -> Unit, onError: (String) -> Unit) {
        val ids = toDelete(group).map { it.fileId }
        if (ids.isEmpty() || ids.size == group.items.size) return
        busy = group.id
        viewModelScope.loadInto({ busy = null; onError(it) }, { api.delete(ids) }) { result ->
            busy = null
            drop(group)
            onDone(ids.filter { id -> result.failed.none { it.fileId == id } }, result.done, result.failed.size)
        }
    }

    /** "Not duplicates": Immich forgets the group. */
    fun dismiss(group: DuplicateGroup, onError: (String) -> Unit) {
        busy = group.id
        viewModelScope.loadInto({ busy = null; onError(it) }, { api.dismissDuplicate(group.id) }) {
            busy = null
            drop(group)
        }
    }
}

private const val PAGE = 20

/** Identical or nearly identical pictures: per group, choose what to keep. */
@Composable
fun DuplicatesScreen() {
    val app = LocalApp.current
    val context = LocalContext.current
    val vm: DuplicatesVm = viewModel { DuplicatesVm(app.api) }
    var limit by rememberSaveable { mutableIntStateOf(PAGE) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = vm.groups
    val failed = stringResource(R.string.action_failed, "%s")

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            item(key = "head") {
                Column(Modifier.statusBarsPadding()) {
                    IconButton(onClick = app.back, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = glimpse.text)
                    }
                    Text(
                        stringResource(R.string.duplicates_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = glimpse.text,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        vm.error != null -> Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.load_failed, vm.error!!), color = glimpse.muted)
                            TextButton(onClick = vm::load) { Text(stringResource(R.string.retry)) }
                        }
                        groups == null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = glimpse.muted)
                        }
                        groups.isEmpty() -> Text(stringResource(R.string.duplicates_none), color = glimpse.muted, modifier = Modifier.padding(16.dp))
                        else -> Text(
                            pluralStringResource(R.plurals.duplicates_intro, groups.size, groups.size) + " " + stringResource(R.string.duplicates_how),
                            style = MaterialTheme.typography.bodyMedium,
                            color = glimpse.muted,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            items(groups.orEmpty().take(limit), key = { it.id }) { group ->
                Group(
                    group = group,
                    keep = vm.keep[group.id].orEmpty(),
                    busy = vm.busy == group.id,
                    onToggle = { vm.toggle(group, it) },
                    onOpen = { item ->
                        val list = group.items.map { it.toItem() }
                        app.open(list, list.first { it.fileId == item.fileId })
                    },
                    onDelete = { confirm = group.id },
                    onDismiss = { vm.dismiss(group) { app.notices.show(failed.replace("%s", it)) } },
                )
            }
            if (groups != null && groups.size > limit) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        QuietButton(stringResource(R.string.duplicates_more), { limit += PAGE })
                    }
                }
            }
        }
        StatusScrim()
    }

    val group = groups?.firstOrNull { it.id == confirm }
    if (group != null) {
        val n = vm.toDelete(group).size
        GlimpseSheet(onDismiss = { confirm = null }) {
            SheetTitle(pluralStringResource(R.plurals.delete_title, n, n), stringResource(R.string.delete_text))
            SheetButtons(
                cancel = stringResource(R.string.cancel),
                confirm = stringResource(R.string.action_delete),
                onCancel = { confirm = null },
                onConfirm = {
                    confirm = null
                    vm.resolve(group, onDone = { deleted, done, bad ->
                        app.deleted = app.deleted + deleted
                        app.notices.show(
                            if (bad > 0) context.resources.getQuantityString(R.plurals.transfer_failed, bad, bad, done)
                            else context.resources.getQuantityString(R.plurals.deleted, done, done),
                        )
                    }, onError = { app.notices.show(failed.replace("%s", it)) })
                },
            )
        }
    }
}

@Composable
private fun Group(
    group: DuplicateGroup,
    keep: Set<Long>,
    busy: Boolean,
    onToggle: (Long) -> Unit,
    onOpen: (DuplicateItem) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val deleting = group.items.count { it.fileId !in keep }
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(glimpse.raised)
            .padding(vertical = 14.dp),
    ) {
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(group.items, key = { it.fileId }) { item ->
                Picture(item, kept = item.fileId in keep, onToggle = { onToggle(item.fileId) }, onOpen = { onOpen(item) })
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                pluralStringResource(R.plurals.duplicates_delete, deleting, deleting),
                onDelete,
                Modifier.weight(1f),
                enabled = deleting in 1 until group.items.size,
                busy = busy,
            )
            Spacer(Modifier.width(10.dp))
            QuietButton(stringResource(R.string.duplicates_dismiss), onDismiss, enabled = !busy)
        }
    }
}

/** One picture: apricot ring and "Keep" when it stays, dimmed and "Delete" when it goes. */
@Composable
private fun Picture(item: DuplicateItem, kept: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val api = LocalApp.current.api
    Column(Modifier.width(150.dp)) {
        Box {
            AsyncImage(
                model = api.thumbnailUrl(item.toItem()),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(glimpse.background)
                    .then(if (kept) Modifier.border(3.dp, glimpse.accent, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable(onClick = onOpen)
                    .alpha(if (kept) 1f else 0.55f)
                    .photoBlur(),
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (kept) glimpse.accent else glimpse.background.copy(alpha = 0.85f))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (kept) Icons.Filled.Check else Icons.Outlined.Delete, null, tint = if (kept) Palette.Night else glimpse.text, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(if (kept) R.string.duplicates_keep else R.string.action_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (kept) Palette.Night else glimpse.text,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(
                if (item.width > 0) "${item.width} × ${item.height}" else null,
                if (item.bytes > 0) size(item.bytes) else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = glimpse.text,
        )
        item.toItem().taken?.let { Text(longDate(it), style = MaterialTheme.typography.bodySmall, color = glimpse.muted) }
        Text(item.path.substringBeforeLast('/').ifEmpty { "/" }, style = MaterialTheme.typography.bodySmall, color = glimpse.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** File size as people read it: 1.2 MB. */
fun size(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(java.util.Locale.getDefault(), "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1e6)
    bytes >= 1_000 -> String.format(java.util.Locale.getDefault(), "%.0f kB", bytes / 1e3)
    else -> "$bytes B"
}

