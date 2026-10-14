package com.infraviored.glimpse.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.data.Memory
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.Paged
import com.infraviored.glimpse.ui.components.Selection
import com.infraviored.glimpse.ui.loadInto
import com.infraviored.glimpse.ui.SensitiveModeSheet
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.sensitiveFilterName
import com.infraviored.glimpse.ui.theme.glimpse
import java.time.LocalDate

/**
 * A timeline, newest first, that can jump to a year and grow from there in both directions.
 * With [person]: only the pictures that person is in (their page), without memories.
 */
class TimelineVm(private val api: Api, val person: String? = null) : ViewModel() {
    /** Start of the list: null for now, else the end of a year jumped to. */
    var before by mutableStateOf<String?>(null)
        private set
    var jumpedTo by mutableStateOf<Int?>(null)
        private set
    val paged = Paged(viewModelScope) { page -> api.timeline(page, before, person = person) }
    /** After a jump: the pictures from the jump point forward to today, loaded as one scrolls up. */
    val newer = Paged(viewModelScope) { page -> api.timeline(page, before = null, after = before, person = person) }
    /** The newer pictures on screen; they join the list in the frame that keeps the view in place. */
    var shownNewer by mutableStateOf<List<Item>>(emptyList())
        private set

    /**
     * Counts jumps; [jumpShown] is the one whose start the list has scrolled to. Kept here, not
     * in the screen: coming back from the viewer the screen is composed anew, and scrolling to
     * the jump's start again threw the list back to the newest pictures loaded.
     */
    var jumpId by mutableIntStateOf(0)
        private set
    var jumpShown = -1

    /** Whether a newer page may load (see TimelineGrid); survives a visit to the viewer. */
    var newerArmed by mutableStateOf(true)

    var years by mutableStateOf<List<Int>>(emptyList())
        private set
    var memories by mutableStateOf<List<Memory>>(emptyList())
        private set

    init {
        paged.loadMore()
        reloadMemories()
    }

    /** What the list shows, newest first, without what was deleted meanwhile. */
    fun items(deleted: Set<Long>): List<Item> = timelineItems(jumpedTo != null, shownNewer, paged.items, deleted)

    fun showNewer(items: List<Item>) {
        shownNewer = items
    }

    fun reloadMemories() {
        if (person != null) return
        viewModelScope.loadInto({}, { api.memories(LocalDate.now().toString()) }) { memories = it.filter { m -> m.items.isNotEmpty() } }
    }

    fun rememberYears() {
        paged.lastPage?.years?.takeIf { it.isNotEmpty() }?.let { years = it }
    }

    fun jump(year: Int?) {
        jumpedTo = jumpTarget(year, years, LocalDate.now().year)
        before = jumpedTo?.let(::yearEnd)
        newer.reset()
        shownNewer = emptyList()
        newerArmed = true
        jumpId++
        paged.reload()
    }

    /** Load again from where the list is (something changed on the server). */
    fun reload() = jump(jumpedTo)

    /** Pictures that left this list. */
    fun remove(fileIds: Set<Long>) {
        paged.remove(fileIds)
        newer.remove(fileIds)
        shownNewer = shownNewer.filterNot { it.fileId in fileIds }
    }
}

@Composable
fun TimelineScreen(
    selection: Selection,
    gridState: LazyGridState,
    bottomPadding: Dp,
    onAccount: () -> Unit,
) {
    val app = LocalApp.current
    val vm: TimelineVm = viewModel { TimelineVm(app.api) }
    // Locked or unlocked: other pictures are visible now
    val privateVersion = app.privateVersion
    LaunchedEffect(privateVersion) {
        if (privateVersion > 0) {
            vm.reload()
            vm.reloadMemories()
        }
    }
    TimelineGrid(
        vm = vm,
        selection = selection,
        gridState = gridState,
        bottomPadding = bottomPadding,
        columnsKey = "timeline",
        empty = stringResource(R.string.timeline_empty),
    ) {
        TopLine(onAccount)
        if (vm.jumpedTo == null && vm.memories.isNotEmpty()) {
            OnThisDay(vm.memories) { memory -> app.open(memory.items, memory.items.first()) }
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
        // A sensitive filter is on: its name, so it is never forgotten; a tap changes it
        sensitiveFilterName(app.sensitive?.mode)?.let { name ->
            var choosing by remember { mutableStateOf(false) }
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                color = glimpse.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(glimpse.raised)
                    .clickable { choosing = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            if (choosing) SensitiveModeSheet(onDismiss = { choosing = false })
        }
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
