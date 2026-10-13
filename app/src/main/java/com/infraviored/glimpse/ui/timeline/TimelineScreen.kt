package com.infraviored.glimpse.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.infraviored.glimpse.data.Item
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.Memory
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.Paged
import com.infraviored.glimpse.ui.byMonth
import com.infraviored.glimpse.ui.components.PhotoGrid
import com.infraviored.glimpse.ui.components.gridKeys
import com.infraviored.glimpse.ui.components.Selection
import com.infraviored.glimpse.ui.loadInto
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class TimelineVm(private val api: Api) : ViewModel() {
    /** Start of the list: null for now, else the end of a year jumped to. */
    var before by mutableStateOf<String?>(null)
        private set
    var jumpedTo by mutableStateOf<Int?>(null)
        private set
    val paged = Paged(viewModelScope) { page -> api.timeline(page, before) }
    /** After a jump: the pictures from the jump point forward to today, loaded as one scrolls up. */
    val newer = Paged(viewModelScope) { page -> api.timeline(page, before = null, after = before) }

    var years by mutableStateOf<List<Int>>(emptyList())
        private set
    var memories by mutableStateOf<List<Memory>>(emptyList())
        private set

    init {
        paged.loadMore()
        reloadMemories()
    }

    fun reloadMemories() {
        viewModelScope.loadInto({}, { api.memories(LocalDate.now().toString()) }) { memories = it.filter { m -> m.items.isNotEmpty() } }
    }

    fun rememberYears() {
        paged.lastPage?.years?.takeIf { it.isNotEmpty() }?.let { years = it }
    }

    fun jump(year: Int?) {
        val now = LocalDate.now().year
        jumpedTo = year?.takeIf { it < now }
        before = jumpedTo?.let { "${it + 1}-01-01T00:00:00.000Z" }
        newer.reset()
        paged.reload()
    }
}

@Composable
fun TimelineScreen(
    selection: Selection,
    gridState: LazyGridState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onAccount: () -> Unit,
) {
    val app = LocalApp.current
    val vm: TimelineVm = viewModel { TimelineVm(app.api) }
    val paged = vm.paged
    val scope = rememberCoroutineScope()
    val unknown = stringResource(R.string.unknown_date)
    val newer = vm.newer
    // The newer pictures on screen: they join the list in the same frame that keeps the
    // pictures in view in place (the grid's own anchoring loses them after many inserts)
    var shownNewer by remember { mutableStateOf<List<Item>>(emptyList()) }
    val all = if (vm.jumpedTo == null) paged.items else shownNewer.asReversed() + paged.items
    val items = all.filterNot { it.fileId in app.deleted }
    val sections = remember(items) { byMonth(items, unknown) }
    LaunchedEffect(paged.lastPage) { vm.rememberYears() }
    // Locked or unlocked: other pictures are visible now
    val privateVersion = app.privateVersion
    LaunchedEffect(privateVersion) {
        if (privateVersion > 0) {
            vm.jump(vm.jumpedTo)
            vm.reloadMemories()
        }
    }

    // The year of the pictures at the top of the screen, for the scrubber's resting label
    val visibleYear by remember(sections) {
        derivedStateOf {
            val key = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contentType == "photo" }?.key as? Long
            items.firstOrNull { it.fileId == key }?.taken?.year
        }
    }

    // After a jump the list opens at that year, just below the header, so that what loads
    // above it (scrolling up towards today) slides in without moving the pictures in view
    LaunchedEffect(vm.jumpedTo, paged.loaded) {
        if (vm.jumpedTo != null && paged.loaded && items.isNotEmpty()) gridState.scrollToItem(1)
    }
    LaunchedEffect(newer.items) {
        val added = newer.items
        if (added.size > shownNewer.size) {
            val visiblePhoto = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contentType == "photo" && it.key is Long }
            val anchorKey = (visiblePhoto?.key as? Long)
                ?: (shownNewer.lastOrNull { it.fileId !in app.deleted } ?: paged.items.firstOrNull { it.fileId !in app.deleted })?.fileId
            if (anchorKey != null) {
                val after = byMonth((added.asReversed() + paged.items).filterNot { it.fileId in app.deleted }, unknown)
                val index = gridKeys(after).indexOf(anchorKey)
                val offset = if (visiblePhoto != null && visiblePhoto.index == gridState.firstVisibleItemIndex) {
                    gridState.firstVisibleItemScrollOffset
                } else {
                    0
                }
                if (index >= 0) gridState.requestScrollToItem(index + 1, offset)
            }
        }
        shownNewer = added
    }
    LaunchedEffect(vm.jumpedTo) {
        if (vm.jumpedTo == null) return@LaunchedEffect
        snapshotFlow { paged.loaded && !newer.loading && newer.hasMore && newer.error == null && gridState.firstVisibleItemIndex < 40 }
            .collect { if (it) newer.loadMore() }
    }

    Box(Modifier.fillMaxSize()) {
        PhotoGrid(
            sections = sections,
            selection = selection,
            onOpen = { app.open(items, it, paged) },
            state = gridState,
            loading = paged.loading,
            error = paged.error,
            onRetry = { paged.loadMore() },
            hasMore = paged.hasMore,
            onLoadMore = { paged.loadMore() },
            contentPadding = PaddingValues(bottom = bottomPadding),
            columnsKey = "timeline",
            empty = stringResource(R.string.timeline_empty),
        ) {
            item(key = "top", span = { GridItemSpan(maxLineSpan) }, contentType = "top") {
                Column(Modifier.statusBarsPadding()) {
                    TopLine(onAccount)
                    if (vm.jumpedTo == null && vm.memories.isNotEmpty()) {
                        OnThisDay(vm.memories) { memory -> app.open(memory.items, memory.items.first()) }
                    }
                    if (vm.jumpedTo != null && newer.loading) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = glimpse.muted)
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

@Composable
private fun TopLine(onAccount: () -> Unit) {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Glimpse", style = MaterialTheme.typography.displaySmall, color = glimpse.text, modifier = Modifier.weight(1f))
        AsyncImage(
            model = "${app.api.account.server}/index.php/avatar/${android.net.Uri.encode(app.api.userId)}/96",
            contentDescription = stringResource(R.string.account),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(8.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(glimpse.raised)
                .clickable(onClick = onAccount).photoBlur(),
        )
    }
}

/** Earlier years on today's date, as tall cards. */
@Composable
private fun OnThisDay(memories: List<Memory>, onOpen: (Memory) -> Unit) {
    val app = LocalApp.current
    val thisYear = LocalDate.now().year
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    ) {
        items(memories, key = { it.year }) { memory ->
            val ago = thisYear - memory.year
            Box(
                Modifier
                    .width(124.dp)
                    .height(168.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(glimpse.raised)
                    .clickable { onOpen(memory) },
            ) {
                AsyncImage(
                    model = app.api.thumbnailUrl(memory.items.first()),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().photoBlur(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))),
                )
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text(
                        memory.year.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                    )
                    Text(
                        pluralStringResource(R.plurals.years_ago, ago, ago),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
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
