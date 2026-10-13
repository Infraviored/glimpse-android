package com.infraviored.glimpse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.infraviored.glimpse.ui.components.Dock
import com.infraviored.glimpse.ui.components.DockAction
import com.infraviored.glimpse.ui.components.selectionMore
import com.infraviored.glimpse.ui.people.PeopleVm
import com.infraviored.glimpse.ui.components.Selection
import com.infraviored.glimpse.ui.components.Tab
import com.infraviored.glimpse.ui.components.selectionActions
import com.infraviored.glimpse.ui.folders.FolderScreen
import com.infraviored.glimpse.ui.people.PeopleScreen
import com.infraviored.glimpse.ui.theme.glimpse
import com.infraviored.glimpse.ui.timeline.TimelineScreen

/** The three places to browse, with the lens and the tabs at the bottom. */
@Composable
fun Home() {
    val app = LocalApp.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selection = remember { Selection() }
    val timelineGrid = rememberLazyGridState()
    val actions = rememberPhotoActions()
    var account by remember { mutableStateOf(false) }
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockHeight = navBar + 132.dp
    val tabs = listOf(Tab(stringResource(R.string.tab_timeline)), Tab(stringResource(R.string.tab_people)), Tab(stringResource(R.string.tab_folders)), Tab(stringResource(R.string.tab_map)))

    val peopleVm: PeopleVm = androidx.lifecycle.viewmodel.compose.viewModel { PeopleVm(app.api) }
    val choosingPeople = tab == 1 && peopleVm.chosen.isNotEmpty()

    BackHandler(enabled = selection.active) { selection.clear() }
    BackHandler(enabled = choosingPeople) { peopleVm.chosen = emptySet() }
    BackHandler(enabled = !selection.active && !choosingPeople && tab != 0) { tab = 0 }
    ActionSheets(actions) { selection.clear() }

    Box(Modifier.fillMaxSize().background(glimpse.background)) {
        Crossfade(tab, label = "tab") { t ->
            when (t) {
                0 -> TimelineScreen(selection, timelineGrid, dockHeight, onAccount = { account = true })
                1 -> PeopleScreen(dockHeight)
                2 -> FolderScreen("/", asTab = true, bottomPadding = dockHeight)
                else -> com.infraviored.glimpse.ui.map.MapScreen(dockHeight)
            }
        }
        com.infraviored.glimpse.ui.components.StatusScrim()
        val timelineItems = if (tab == 0) currentTimelineItems() else emptyList()
        Dock(
            tabs = tabs,
            current = tab,
            onTab = { if (it == tab && it == 0) { /* top */ } else { selection.clear(); peopleVm.chosen = emptySet(); tab = it } },
            onLens = { app.search() },
            selectedCount = if (tab == 1) peopleVm.chosen.size else selection.ids.size,
            actions = if (tab == 1) peopleActions(peopleVm) else selectionActions(actions, selection, timelineItems),
            onClearSelection = { selection.clear(); peopleVm.chosen = emptySet() },
            modifier = Modifier.align(Alignment.BottomCenter),
            more = if (tab == 1) emptyList() else selectionMore(actions, selection, timelineItems),
        )
    }

    var unlock by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { app.loadPrivate() }
    if (account) AccountSheet(onDismiss = { account = false }, onUnlock = { account = false; unlock = true })
    if (unlock) UnlockSheet(onDismiss = { unlock = false })
}

/** What can be done with the chosen people: favorite, hide, merge. */
@Composable
private fun peopleActions(vm: PeopleVm): List<DockAction> {
    val app = LocalApp.current
    val chosen = vm.chosenPeople()
    val allFavorite = chosen.isNotEmpty() && chosen.all { it.favorite }
    val allHidden = chosen.isNotEmpty() && chosen.all { it.hidden }
    val onError: (String) -> Unit = { app.notices.show(it) }
    return listOf(
        DockAction(
            if (allFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
            stringResource(if (allFavorite) R.string.person_favorite_on else R.string.person_favorite),
        ) { vm.update(vm.chosen, favorite = !allFavorite, onError = onError); vm.chosen = emptySet() },
        DockAction(
            if (allHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            stringResource(if (allHidden) R.string.person_show else R.string.person_hide),
        ) { vm.update(vm.chosen, hidden = !allHidden, onError = onError); vm.chosen = emptySet() },
        DockAction(Icons.AutoMirrored.Outlined.CallMerge, stringResource(R.string.merge_action), enabled = chosen.size >= 2) { vm.merging = true },
    )
}

/** The timeline's loaded pictures, for the selection's actions. */
@Composable
private fun currentTimelineItems(): List<com.infraviored.glimpse.data.Item> {
    val app = LocalApp.current
    val vm: com.infraviored.glimpse.ui.timeline.TimelineVm = androidx.lifecycle.viewmodel.compose.viewModel { com.infraviored.glimpse.ui.timeline.TimelineVm(app.api) }
    return vm.paged.items
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(onDismiss: () -> Unit, onUnlock: () -> Unit) {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val failed = stringResource(R.string.action_failed, "%s")
    androidx.compose.runtime.LaunchedEffect(Unit) { app.loadPrivate() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = "${app.api.account.server}/index.php/avatar/${android.net.Uri.encode(app.api.userId)}/128",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(glimpse.raised).photoBlur(),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(app.api.account.user, style = MaterialTheme.typography.headlineSmall)
                    Text(app.api.account.server.removePrefix("https://"), style = MaterialTheme.typography.bodyMedium, color = glimpse.muted)
                }
            }
            Spacer(Modifier.height(20.dp))
            app.privateArea?.let { p ->
                val count = p.files + p.folderCount + p.peopleCount
                AccountRow(
                    if (p.unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    stringResource(if (p.unlocked) R.string.private_lock else R.string.private_unlock_area),
                    if (p.unlocked) stringResource(R.string.private_unlocked_now) else if (count == 0) stringResource(R.string.private_empty) else stringResource(R.string.private_locked_now),
                ) {
                    if (!p.unlocked && p.askPassword) onUnlock() else app.togglePrivate(scope) { app.notices.show(failed.replace("%s", it)) }
                }
            }
            AccountRow(Icons.Outlined.ContentCopy, stringResource(R.string.duplicates_title), stringResource(R.string.duplicates_subtitle)) {
                onDismiss()
                app.duplicates()
            }
            AccountRow(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.account_open_web)) {
                uri.openUri(app.api.account.glimpseUrl)
            }
            AccountRow(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.account_logout)) {
                onDismiss()
                app.logout()
            }
        }
    }
}

@Composable
private fun AccountRow(icon: ImageVector, label: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = glimpse.text)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = glimpse.muted) }
        }
    }
}
