package com.infraviored.glimpse.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.Section
import com.infraviored.glimpse.ui.photoElement
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.flow.distinctUntilChanged

/** Which pictures are chosen for an action; empty means not selecting. */
class Selection(initial: Set<Long> = emptySet()) {
    var ids by androidx.compose.runtime.mutableStateOf(initial)
    val active: Boolean get() = ids.isNotEmpty()
    fun toggle(id: Long) {
        ids = if (id in ids) ids - id else ids + id
    }
    fun clear() {
        ids = emptySet()
    }
}

private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 7

/**
 * Pictures as a square grid, sections with month headings and year bands.
 *
 * Tap opens, long press starts selecting (and dragging on selects a run), two fingers
 * pinch the grid between [MIN_COLUMNS] and [MAX_COLUMNS] columns.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGrid(
    sections: List<Section>,
    selection: Selection,
    onOpen: (Item) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    loading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    columnsKey: String = "grid",
    empty: String? = null,
    header: LazyGridScope.() -> Unit = {},
) {
    var columns by rememberSaveable(columnsKey) { mutableIntStateOf(4) }
    val haptics = LocalHapticFeedback.current
    val all = remember(sections) { sections.flatMap { it.items } }
    val latestAll by rememberUpdatedState(all)

    // Ask for more before the end is on screen
    val nearEnd by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - columns * 6
        }
    }
    val latestLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, hasMore) {
        snapshotFlow { nearEnd }.distinctUntilChanged().collect { if (it && hasMore) latestLoadMore() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoom = 1f
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            zoom *= event.calculateZoom()
                            if (zoom > 1.35f && columns > MIN_COLUMNS) {
                                columns--
                                zoom = 1f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (zoom < 0.74f && columns < MAX_COLUMNS) {
                                columns++
                                zoom = 1f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                // Long press selects (the thumbnail's own long click); keep holding and drag to
                // add every picture between it and the finger.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                            if (!change.pressed || event.changes.size > 1) return@withTimeoutOrNull true
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                        }
                        @Suppress("UNREACHABLE_CODE")
                        true
                    }
                    if (early != null) return@awaitEachGesture
                    val start = indexAt(state, latestAll, down.position.x, down.position.y)
                    if (start < 0) return@awaitEachGesture
                    // What was chosen before this gesture stays; the run follows the finger, also back
                    val base = selection.ids + latestAll[start].fileId
                    // The thumbnail's long click consumes the moves that follow; read them anyway
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val to = indexAt(state, latestAll, change.position.x, change.position.y)
                        if (to >= 0) {
                            selection.ids = base + (minOf(start, to)..maxOf(start, to)).map { latestAll[it].fileId }
                        }
                        change.consume()
                    }
                }
            },
    ) {
        header()
        sections.forEach { section ->
            section.year?.let { year ->
                item(key = "year-${section.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "year") {
                    YearBand(year)
                }
            }
            item(key = "head-${section.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "head") {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = glimpse.text,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
                )
            }
            items(section.items, key = { it.fileId }, contentType = { "photo" }) { item ->
                Thumbnail(
                    item = item,
                    selected = item.fileId in selection.ids,
                    selecting = selection.active,
                    onClick = { if (selection.active) selection.toggle(item.fileId) else onOpen(item) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selection.toggle(item.fileId)
                    },
                )
            }
        }
        // No key: a keyed footer would keep its place and drag the grid down when results arrive above it
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                when {
                    error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.load_failed, error), color = glimpse.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                    loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = glimpse.muted)
                    all.isEmpty() && empty != null -> Column(Modifier.padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(empty, color = glimpse.muted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        if (hasMore) TextButton(onClick = onLoadMore) { Text(stringResource(R.string.search_further)) }
                    }
                }
            }
        }
    }
}

/** The year, set big: scrolling past one is a step further back in time. */
@Composable
fun YearBand(year: Int) {
    Text(
        year.toString(),
        style = MaterialTheme.typography.displayLarge,
        color = glimpse.text,
        modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 40.dp, bottom = 0.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumbnail(
    item: Item,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val api = LocalApp.current.api
    val inset by animateFloatAsState(if (selected) 0.86f else 1f, label = "inset")
    val corner by animateDpAsState(if (selected) 14.dp else 0.dp, label = "corner")
    Box(
        modifier
            .aspectRatio(1f)
            .background(glimpse.raised)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = api.thumbnailUrl(item),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(inset)
                .clip(RoundedCornerShape(corner))
                .photoElement(item.fileId).photoBlur(),
        )
        if (item.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    .padding(2.dp),
            )
        }
        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(22.dp)
                    .then(
                        if (selected) Modifier.background(glimpse.accent, CircleShape)
                        else Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape).background(Color.Black.copy(alpha = 0.15f), CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, null, tint = Color(0xFF14162B), modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** The picture under a point of the grid, as an index into [all]; -1 between pictures. */
private fun indexAt(state: LazyGridState, all: List<Item>, x: Float, y: Float): Int {
    val hit = state.layoutInfo.visibleItemsInfo.firstOrNull {
        it.contentType == "photo" &&
            x >= it.offset.x && x < it.offset.x + it.size.width &&
            y >= it.offset.y && y < it.offset.y + it.size.height
    } ?: return -1
    val id = hit.key as? Long ?: return -1
    return all.indexOfFirst { it.fileId == id }
}
