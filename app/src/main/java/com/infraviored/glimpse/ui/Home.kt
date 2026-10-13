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
import com.infraviored.glimpse.ui.components.Dock
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
    val tabs = listOf(Tab(stringResource(R.string.tab_timeline)), Tab(stringResource(R.string.tab_people)), Tab(stringResource(R.string.tab_folders)))

    BackHandler(enabled = selection.active) { selection.clear() }
    BackHandler(enabled = !selection.active && tab != 0) { tab = 0 }
    DeleteDialog(actions) { selection.clear() }

    Box(Modifier.fillMaxSize().background(glimpse.background)) {
        Crossfade(tab, label = "tab") { t ->
            when (t) {
                0 -> TimelineScreen(selection, timelineGrid, dockHeight, onAccount = { account = true })
                1 -> PeopleScreen(dockHeight)
                else -> FolderScreen("/", asTab = true, bottomPadding = dockHeight)
            }
        }
        com.infraviored.glimpse.ui.components.StatusScrim()
        val timelineItems = if (tab == 0) currentTimelineItems() else emptyList()
        Dock(
            tabs = tabs,
            current = tab,
            onTab = { if (it == tab && it == 0) { /* top */ } else { selection.clear(); tab = it } },
            onLens = { app.search() },
            selectedCount = selection.ids.size,
            actions = selectionActions(actions, selection, timelineItems),
            onClearSelection = selection::clear,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (account) AccountSheet(onDismiss = { account = false })
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
private fun AccountSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
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
private fun AccountRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = glimpse.text)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
