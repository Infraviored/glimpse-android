package com.infraviored.glimpse.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Item
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Sell
import com.infraviored.glimpse.ui.ActionSheets
import com.infraviored.glimpse.ui.PhotoActions
import com.infraviored.glimpse.ui.rememberPhotoActions

/** The actions for the chosen pictures, in the order the dock shows them. */
@Composable
fun selectionActions(actions: PhotoActions, selection: Selection, items: List<Item>): List<DockAction> {
    fun chosen() = items.filter { it.fileId in selection.ids }
    val app = com.infraviored.glimpse.ui.LocalApp.current
    val unlocked = app.privateArea?.unlocked == true
    val single = items.singleOrNull { it.fileId in selection.ids }?.takeIf { selection.ids.size == 1 && it.id != null }
    return listOf(
        DockAction(
            Icons.Outlined.AutoAwesomeMosaic,
            stringResource(R.string.action_similar),
            subtitle = if (single == null) stringResource(R.string.similar_one) else null,
            enabled = single != null,
        ) {
            single?.let { item ->
                selection.clear()
                app.search(similar = item.id!!, label = item.taken?.let { com.infraviored.glimpse.ui.longDate(it) } ?: item.name)
            }
        },
        DockAction(Icons.Outlined.Share, stringResource(R.string.action_share)) { actions.share(chosen()) },
        DockAction(Icons.Outlined.FavoriteBorder, stringResource(R.string.action_favorite)) { actions.favorite(chosen()); selection.clear() },
        DockAction(Icons.Outlined.Download, stringResource(R.string.action_download)) { actions.download(chosen()); selection.clear() },
        DockAction(Icons.Outlined.Delete, stringResource(R.string.action_delete)) { actions.confirmDelete = chosen() },
    )
}

/** The further actions, behind the dock's "more" button. */
@Composable
fun selectionMore(actions: PhotoActions, selection: Selection, items: List<Item>): List<DockAction> {
    fun chosen() = items.filter { it.fileId in selection.ids }
    val app = com.infraviored.glimpse.ui.LocalApp.current
    val unlocked = app.privateArea?.unlocked == true
    val single = items.singleOrNull { it.fileId in selection.ids }?.takeIf { selection.ids.size == 1 && it.id != null }
    return listOf(
        DockAction(
            Icons.Outlined.AutoAwesomeMosaic,
            stringResource(R.string.action_similar),
            subtitle = if (single == null) stringResource(R.string.similar_one) else null,
            enabled = single != null,
        ) {
            single?.let { item ->
                selection.clear()
                app.search(similar = item.id!!, label = item.taken?.let { com.infraviored.glimpse.ui.longDate(it) } ?: item.name)
            }
        },
        DockAction(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(R.string.action_move)) { actions.pickFolder(chosen(), copy = false) },
        DockAction(Icons.Outlined.FileCopy, stringResource(R.string.action_copy)) { actions.pickFolder(chosen(), copy = true) },
        DockAction(Icons.Outlined.PhotoAlbum, stringResource(R.string.action_album)) { actions.albumFor = chosen() },
        DockAction(Icons.Outlined.Sell, stringResource(R.string.action_tag)) { actions.tagFor = chosen() },
        DockAction(Icons.Outlined.HeartBroken, stringResource(R.string.action_unfavorite)) { actions.favorite(chosen(), on = false); selection.clear() },
        DockAction(Icons.Outlined.Lock, stringResource(R.string.action_private)) { actions.setPrivate(chosen(), on = true) { selection.clear() } },
        DockAction(
            Icons.Outlined.LockOpen,
            stringResource(R.string.action_not_private),
            subtitle = if (unlocked) null else stringResource(R.string.private_unlock_first),
            enabled = unlocked,
        ) { actions.setPrivate(chosen(), on = false) { selection.clear() } },
    )
}

/**
 * A screen with a grid of pictures that are not one of the tabs (a person, a folder,
 * results): the selection dock rises only while pictures are chosen.
 */
@Composable
fun SelectionScaffold(
    selection: Selection,
    items: List<Item>,
    extra: List<DockAction> = emptyList(),
    content: @Composable (bottomPadding: Dp) -> Unit,
) {
    val actions = rememberPhotoActions()
    BackHandler(enabled = selection.active) { selection.clear() }
    ActionSheets(actions) { selection.clear() }
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        content(navBar + if (selection.active) 96.dp else 16.dp)
        StatusScrim()
        AnimatedVisibility(
            visible = selection.active,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Dock(
                tabs = emptyList(),
                current = 0,
                onTab = {},
                onLens = {},
                selectedCount = selection.ids.size.coerceAtLeast(1),
                actions = selectionActions(actions, selection, items),
                onClearSelection = selection::clear,
                more = selectionMore(actions, selection, items) + extra,
            )
        }
    }
}
