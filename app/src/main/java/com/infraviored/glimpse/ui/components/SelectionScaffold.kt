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
import com.infraviored.glimpse.ui.DeleteDialog
import com.infraviored.glimpse.ui.PhotoActions
import com.infraviored.glimpse.ui.rememberPhotoActions

/** The actions for the chosen pictures, in the order the dock shows them. */
@Composable
fun selectionActions(actions: PhotoActions, selection: Selection, items: List<Item>): List<DockAction> {
    fun chosen() = items.filter { it.fileId in selection.ids }
    return listOf(
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
    return emptyList()
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
    DeleteDialog(actions) { selection.clear() }
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
