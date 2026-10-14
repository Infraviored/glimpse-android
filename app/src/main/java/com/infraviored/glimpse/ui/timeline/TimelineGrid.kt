package com.infraviored.glimpse.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.R
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.byMonth
import com.infraviored.glimpse.ui.components.PhotoGrid
import com.infraviored.glimpse.ui.components.Selection
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A timeline: newest first in months and year bands, the year scrubber on the right edge,
 * and after a jump the list grows in both directions with "Back to today". Used by the
 * Timeline tab and by a person's page; [header] is the grid's first item.
 */
@Composable
fun TimelineGrid(
    vm: TimelineVm,
    selection: Selection,
    gridState: LazyGridState,
    bottomPadding: Dp,
    columnsKey: String,
    empty: String? = null,
    header: @Composable ColumnScope.() -> Unit,
) {
    val app = LocalApp.current
    val paged = vm.paged
    val newer = vm.newer
    val scope = rememberCoroutineScope()
    val unknown = stringResource(R.string.unknown_date)
    val items = vm.items(app.deleted)
    val sections = remember(items) { byMonth(items, unknown) }
    LaunchedEffect(paged.lastPage) { vm.rememberYears() }

    // The year of the pictures at the top of the screen, for the scrubber's resting label
    val visibleYear by remember(sections) {
        derivedStateOf {
            val key = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contentType == "photo" }?.key as? Long
            items.firstOrNull { it.fileId == key }?.taken?.year
        }
    }

    // After a jump the list opens at that year, just below the header, so that what loads
    // above it (scrolling up towards today) slides in without moving the pictures in view
    LaunchedEffect(vm.jumpId, paged.loaded) {
        if (vm.jumpedTo != null && paged.loaded && items.isNotEmpty() && vm.jumpShown != vm.jumpId) {
            vm.jumpShown = vm.jumpId
            gridState.scrollToItem(HEADER_ITEMS)
        }
    }
    // The newer pictures join the list in the same frame that keeps the pictures in view in
    // place (the grid's own anchoring loses them after many inserts). The anchor is always a
    // picture: its key stays the same when pages are added, headings and year bands do not,
    // and the header (index 0) would leave the new pictures in view, so their top would load
    // the next page at once – the list ran up year after year.
    LaunchedEffect(newer.items) {
        val added = newer.items
        if (added.size > vm.shownNewer.size) {
            val visible = gridState.layoutInfo.visibleItemsInfo
            val photo = visible.firstOrNull { it.contentType == "photo" && it.key is Long }
            val anchorKey = (photo?.key as? Long) ?: fallbackAnchor(vm.shownNewer, paged.items, app.deleted)
            val index = if (anchorKey != null) anchorIndex(added, paged.items, app.deleted, anchorKey, unknown) else -1
            if (index >= 0) {
                // Exactly in place: the item at the top of the screen (say the year band right
                // after a jump) and its offset, when it is still the same distance above the
                // picture – true for a year band or heading the new pictures do not merge into,
                // never for the header, which gets the new pictures right below it
                val top = visible.firstOrNull()
                val topIndex = top?.key?.let { anchorIndex(added, paged.items, app.deleted, it, unknown) } ?: -1
                if (top != null && photo != null && topIndex >= 0 && index - topIndex == photo.index - top.index) {
                    gridState.requestScrollToItem(topIndex, gridState.firstVisibleItemScrollOffset)
                } else {
                    val offset = if (photo != null && photo.index == gridState.firstVisibleItemIndex) gridState.firstVisibleItemScrollOffset else 0
                    gridState.requestScrollToItem(index, offset)
                }
            }
        }
        vm.showNewer(added)
    }
    // Newer pages load while the top of the list is near, but after the first one only once
    // the user has scrolled up since: a year with few pictures fits on the screen, its top
    // stays in view whatever is added above, and every page would load the next one.
    LaunchedEffect(vm.jumpedTo, newer.loaded) {
        if (!newer.loaded) vm.newerArmed = true
    }
    LaunchedEffect(vm.jumpedTo) {
        if (vm.jumpedTo == null) return@LaunchedEffect
        // every step of the user's scrolling up counts, also within one long fling
        snapshotFlow {
            val arm = shouldArmNewer(
                isScrollInProgress = gridState.isScrollInProgress,
                scrolledBackward = gridState.lastScrolledBackward,
                canScrollBackward = gridState.canScrollBackward,
                canScrollForward = gridState.canScrollForward,
            )
            Triple(arm, gridState.firstVisibleItemScrollOffset, newer.items.size)
        }.collect { (arm) -> if (arm) vm.newerArmed = true }
    }
    LaunchedEffect(vm.jumpedTo) {
        if (vm.jumpedTo == null) return@LaunchedEffect
        snapshotFlow { vm.newerArmed && paged.loaded && !newer.loading && newer.hasMore && newer.error == null && gridState.firstVisibleItemIndex < 40 }
            .collect {
                if (it) {
                    vm.newerArmed = false
                    newer.loadMore()
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        PhotoGrid(
            sections = sections,
            selection = selection,
            onOpen = { app.open(items, it, viewerSource(vm.jumpedTo != null, paged), gridKey = columnsKey) },
            state = gridState,
            loading = paged.loading,
            error = paged.error,
            onRetry = { paged.loadMore() },
            hasMore = paged.hasMore,
            onLoadMore = { paged.loadMore() },
            contentPadding = PaddingValues(bottom = bottomPadding),
            columnsKey = columnsKey,
            gridKey = columnsKey,
            headerCount = HEADER_ITEMS,
            empty = empty,
        ) {
            item(key = "top", span = { GridItemSpan(maxLineSpan) }, contentType = "top") {
                Column(Modifier.statusBarsPadding()) {
                    header()
                    if (vm.jumpedTo != null && newer.loading) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = glimpse.muted)
                        }
                    } else if (vm.jumpedTo != null && newer.error != null) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { newer.loadMore() }) {
                                Text(stringResource(R.string.retry), color = glimpse.muted)
                            }
                        }
                    }
                }
            }
        }

        // Far from today after a jump: one tap back to the newest pictures
        AnimatedVisibility(
            visible = vm.jumpedTo != null && newer.hasMore && gridState.firstVisibleItemIndex > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 16.dp),
        ) {
            Text(
                stringResource(R.string.timeline_back_to_now),
                color = glimpse.text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(glimpse.raised)
                    .clickable {
                        selection.clear()
                        vm.jump(null)
                        scope.launch { gridState.scrollToItem(0) }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // The scrubber shows while scrolling and a moment after, like a scroll bar
        var scrubberShown by remember { mutableStateOf(false) }
        val scrolledIn by remember { derivedStateOf { gridState.isScrollInProgress && gridState.firstVisibleItemIndex > 0 } }
        LaunchedEffect(scrolledIn) {
            if (scrolledIn) scrubberShown = true
            else { kotlinx.coroutines.delay(1800); scrubberShown = false }
        }
        if (vm.years.size > 1) {
            YearScrubber(
                shown = scrubberShown,
                years = vm.years,
                restingYear = visibleYear,
                onPick = { year ->
                    selection.clear()
                    vm.jump(year)
                    scope.launch { gridState.scrollToItem(0) }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .padding(top = 72.dp, bottom = bottomPadding),
            )
        }
    }
}

/**
 * The right edge is time: drag along it and the year under the finger appears big; let go
 * and the timeline starts at the end of that year.
 */
@Composable
private fun YearScrubber(
    shown: Boolean,
    years: List<Int>,
    restingYear: Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    val sorted = remember(years) { years.sortedDescending() }
    val picked = sorted[(fraction * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)]
    var lastPicked by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(picked, dragging) {
        if (dragging && picked != lastPicked) {
            lastPicked = picked
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val handleAlpha by animateFloatAsState(if (dragging) 1f else 0.85f, label = "handle")

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val restFraction = restingYear?.let { y -> sorted.indexOf(y).takeIf { it >= 0 }?.toFloat()?.div((sorted.size - 1).coerceAtLeast(1)) } ?: 0f
        val pos = if (dragging) fraction else restFraction
        val handleY = with(LocalDensity.current) { (pos * (heightPx - 40.dp.toPx())).roundToInt() }

        // The touch strip: only the outer 36 dp, and only while the handle shows; the rest stays the grid's
        if (shown || dragging) Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(36.dp)
                .fillMaxHeight()
                .pointerInput(sorted) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            fraction = (offset.y / size.height).coerceIn(0f, 1f)
                        },
                        onVerticalDrag = { change, _ ->
                            fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                            change.consume()
                        },
                        onDragEnd = {
                            dragging = false
                            onPick(sorted[(fraction * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)])
                        },
                        onDragCancel = { dragging = false },
                    )
                },
        )
        // Resting handle: a small tab with the year in view
        val handleVisible by animateFloatAsState(if (shown || dragging) 1f else 0f, label = "handleVisible")
        Box(
            Modifier
                .graphicsLayer { alpha = handleVisible }
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, handleY) }
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 6.dp, bottomEnd = 6.dp))
                .background(glimpse.raised.copy(alpha = handleAlpha))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                (if (dragging) picked else restingYear ?: sorted.first()).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = glimpse.text,
            )
        }
        // While dragging: the year, as big as the screen allows
        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, (handleY - 48.dp.toPx()).roundToInt().coerceAtLeast(0)) },
        ) {
            Text(
                picked.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = glimpse.text,
                modifier = Modifier
                    .padding(end = 56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(glimpse.background.copy(alpha = 0.88f))
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
    }
}
