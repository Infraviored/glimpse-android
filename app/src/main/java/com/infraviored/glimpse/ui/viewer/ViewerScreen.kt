package com.infraviored.glimpse.ui.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Explicit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.infraviored.glimpse.R
import com.infraviored.glimpse.ui.theme.glimpse
import com.infraviored.glimpse.data.Faces
import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.ActionSheets
import com.infraviored.glimpse.ui.PhotoActions
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Sell
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.longDate
import com.infraviored.glimpse.ui.photoElement
import com.infraviored.glimpse.ui.rememberPhotoActions
import com.infraviored.glimpse.ui.shortTime
import com.infraviored.glimpse.ui.theme.Palette
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import kotlin.math.abs
import kotlin.math.roundToInt

/** Full screen, one picture at a time, swiping through the list it was opened from. */
@Composable
fun ViewerScreen() {
    val app = LocalApp.current
    val request = app.viewer ?: run {
        LaunchedEffect(Unit) { app.back() }
        return
    }
    val source = request.source
    val items = (source?.items ?: request.items).filterNot { it.fileId in app.deleted }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { app.back() }
        return
    }
    val pager = rememberPagerState(initialPage = request.start.coerceIn(0, items.lastIndex)) { items.size }
    val current = items.getOrNull(pager.currentPage) ?: items.last()
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }
    val actions = rememberPhotoActions()
    val scope = rememberCoroutineScope()
    val dismiss = remember { Animatable(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 140.dp.toPx() }

    val gridKey = request.gridKey
    LaunchedEffect(current.fileId, gridKey) {
        app.lastViewed = gridKey?.let { com.infraviored.glimpse.ui.RestoreTarget(it, current.fileId) }
    }

    // Page further than what is loaded
    LaunchedEffect(pager.currentPage, items.size) {
        if (source != null && source.hasMore && pager.currentPage >= items.size - 4) source.loadMore()
    }

    ImmersiveBars(visible = chrome)
    // Once: the viewer stays on screen while it fades out, and a second back (or a swipe)
    // then would close the screen below it too
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        app.back()
    }
    BackHandler(enabled = !closing) { close() }
    ActionSheets(actions) {}
    // Free the list once this viewer is gone – unless a new one was opened meanwhile (a quick
    // tap during the exit animation), whose request must stay
    DisposableEffect(request) { onDispose { if (app.viewer === request) app.viewer = null } }

    val fade = (1f - abs(dismiss.value) / (threshold * 2.5f)).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = fade))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> scope.launch { dismiss.snapTo(dismiss.value + delta) } },
                onDragStopped = {
                    if (abs(dismiss.value) > threshold) close() else dismiss.animateTo(0f)
                },
            ),
    ) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            key = { items[it].fileId },
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dismiss.value.roundToInt()) }
                .graphicsLayer {
                    val s = 1f - (abs(dismiss.value) / (threshold * 8f)).coerceIn(0f, 0.25f)
                    scaleX = s
                    scaleY = s
                },
        ) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize().then(if (page == pager.currentPage) Modifier.photoElement(item.fileId) else Modifier)) {
                if (item.isVideo) {
                    VideoPage(item, active = page == pager.currentPage, onTap = { chrome = !chrome })
                } else {
                    PhotoPage(item, onTap = { chrome = !chrome })
                }
            }
        }

        AnimatedVisibility(chrome && dismiss.value == 0f, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            TopBar(current, onBack = app.back)
        }
        AnimatedVisibility(chrome && dismiss.value == 0f, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val sensitive = app.isSensitive(current)
            BottomBar(
                listOfNotNull(
                    BarAction(Icons.Outlined.Share, stringResource(R.string.action_share)) { actions.share(listOf(current)) },
                    BarAction(Icons.Outlined.FavoriteBorder, stringResource(R.string.action_favorite)) { actions.favorite(listOf(current)) },
                    BarAction(Icons.Outlined.AutoAwesomeMosaic, stringResource(R.string.action_similar_short), enabled = current.id != null) {
                        current.id?.let { app.search(similar = it, label = current.taken?.let { t -> longDate(t) } ?: current.name) }
                    },
                    BarAction(Icons.Outlined.Info, stringResource(R.string.action_info)) { info = true },
                    // Only with sensitive pictures turned on: a toggle, lit while the picture counts as
                    // sensitive (a longer label for the other state did not fit six actions)
                    if (app.sensitive != null) {
                        BarAction(Icons.Outlined.Explicit, stringResource(R.string.sensitive_mark), on = sensitive) {
                            actions.markSensitive(listOf(current), !sensitive)
                        }
                    } else {
                        null
                    },
                    BarAction(Icons.Outlined.Delete, stringResource(R.string.action_delete)) { actions.confirmDelete = listOf(current) },
                ),
            )
        }
    }

    if (info) InfoSheet(current, actions, onDismiss = { info = false })
}

@Composable
private fun PhotoPage(item: Item, onTap: () -> Unit) {
    val api = LocalApp.current.api
    val context = LocalContext.current
    val state = rememberZoomableImageState()
    ZoomableAsyncImage(
        model = ImageRequest.Builder(context)
            .data(api.previewUrl(item))
            // The grid's thumbnail stands in until the large picture arrives
            .placeholderMemoryCacheKey(api.thumbnailUrl(item))
            .build(),
        contentDescription = item.name,
        state = state,
        contentScale = ContentScale.Fit,
        onClick = { onTap() },
        modifier = Modifier.fillMaxSize().photoBlur(),
    )
}

/** Hide status and navigation bars along with the viewer's own controls. */
@Composable
private fun ImmersiveBars(visible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(visible) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun TopBar(item: Item, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = Color.White) }
        Column(Modifier.weight(1f)) {
            val taken = item.taken
            Text(
                taken?.let { longDate(it) } ?: item.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (taken != null) {
                Text(shortTime(taken), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

private class BarAction(val icon: ImageVector, val label: String, val enabled: Boolean = true, val on: Boolean = false, val onClick: () -> Unit)

@Composable
private fun BottomBar(actions: List<BarAction>) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            .navigationBarsPadding()
            .padding(top = 28.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        actions.forEach { a ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = a.enabled, onClick = a.onClick)
                    .graphicsLayer { alpha = if (a.enabled) 1f else 0.4f }
                    .padding(horizontal = if (actions.size > 5) 2.dp else 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(a.icon, null, tint = if (a.on) glimpse.accent else Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    a.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** When and where it was taken, who is in it, and what to do from here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(item: Item, actions: PhotoActions, onDismiss: () -> Unit) {
    val app = LocalApp.current
    var faces by remember(item.fileId) { mutableStateOf<Faces?>(null) }
    LaunchedEffect(item.fileId) {
        item.id?.let { id -> faces = runCatching { app.api.faces(id) }.getOrNull() }
    }
    val path = app.api.pathOf(item)
    val folder = path.substringBeforeLast('/', "").ifEmpty { "/" }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            item.taken?.let { taken ->
                Text(longDate(taken), style = MaterialTheme.typography.headlineSmall)
                Text(shortTime(taken), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
            val known = faces?.faces.orEmpty().filter { it.personId != null && it.known }.distinctBy { it.personId }
            if (known.isNotEmpty()) {
                Text(stringResource(R.string.info_people), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(known, key = { it.id }) { face ->
                        Column(
                            Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable { onDismiss(); app.person(face.personId!!) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(
                                model = app.api.faceUrl(face.personId!!),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).photoBlur(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                face.personName.orEmpty().ifBlank { stringResource(R.string.unnamed) },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            SheetRow(Icons.Outlined.Folder, path.substringAfterLast('/'), folder) { onDismiss(); app.folder(folder) }
            if (item.id != null) {
                SheetRow(Icons.Outlined.AutoAwesomeMosaic, stringResource(R.string.action_similar), null) {
                    onDismiss()
                    app.search(similar = item.id, label = item.taken?.let { longDate(it) } ?: item.name)
                }
            }
            SheetRow(Icons.Outlined.Download, stringResource(R.string.action_download), null) { onDismiss(); actions.download(listOf(item)) }
            SheetRow(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(R.string.action_move), null) { onDismiss(); actions.pickFolder(listOf(item), copy = false) }
            SheetRow(Icons.Outlined.FileCopy, stringResource(R.string.action_copy), null) { onDismiss(); actions.pickFolder(listOf(item), copy = true) }
            SheetRow(Icons.Outlined.PhotoAlbum, stringResource(R.string.action_album), null) { onDismiss(); actions.albumFor = listOf(item) }
            SheetRow(Icons.Outlined.Sell, stringResource(R.string.action_tag), null) { onDismiss(); actions.tagFor = listOf(item) }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.Apricot, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
