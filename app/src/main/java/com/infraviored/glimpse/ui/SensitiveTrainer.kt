package com.infraviored.glimpse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.data.SensitiveMarks
import com.infraviored.glimpse.data.SensitiveStatus
import com.infraviored.glimpse.ui.components.PhotoGrid
import com.infraviored.glimpse.ui.components.PillField
import com.infraviored.glimpse.ui.components.Selection
import com.infraviored.glimpse.ui.components.StatusScrim
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Wait this long after the last mark before learning, so a few marks in a row train once. */
private const val TRAIN_DELAY = 1500L

/** Pages in a row that may come back empty before the list stops asking. */
private const val EMPTY_PAGES = 5

/**
 * Training the sensitive filter, as on the web: "Visible" shows what the filter lets through
 * (most sensitive-looking first, or sorted by a term), "Hidden" what it hides that the user did
 * not mark (most harmless first). Marked pictures leave the list at once and the filter learns
 * in the background; the list keeps its order until asked ("Sort again").
 */
class TrainerVm(private val api: Api) : ViewModel() {
    var side by mutableStateOf("visible")
        private set
    var query by mutableStateOf("")

    /** The term the list is sorted by (what was submitted, not what is being typed). */
    var searched by mutableStateOf("")
        private set
    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf<SensitiveStatus?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Learning from the marks in the background. */
    var training by mutableStateOf(false)
        private set

    /** Learned at least once here: show what that changed. */
    var trained by mutableStateOf(false)
        private set

    /** The filter learned since the list was loaded: its order may be different now. */
    var resort by mutableStateOf(false)
        private set

    /** Something was marked here: the other lists load again on leaving. */
    var marked = false
        private set

    private var next = 0
    private var job: Job? = null
    private var timer: Job? = null
    private var trainAgain = false

    init {
        viewModelScope.launch {
            loadStatus()
            load(true)
        }
    }

    suspend fun loadStatus() {
        runCatching { api.sensitiveStatus() }.onSuccess { status = it }
    }

    /** From the top (a new term or side) or the next page. */
    fun load(fresh: Boolean) {
        if (fresh) {
            job?.cancel()
            items = emptyList()
            next = 0
            done = false
            resort = false
            loading = false
        } else if (loading || done) {
            return
        }
        val term = if (side == "visible") (if (fresh) query.trim() else searched) else ""
        loading = true
        error = null
        job = viewModelScope.launch {
            try {
                var result = api.sensitiveReview(side, term, next)
                var empty = 0
                while (result.items.isEmpty() && result.nextPage != null && ++empty < EMPTY_PAGES) {
                    result = api.sensitiveReview(side, term, result.nextPage!!)
                }
                val shown = items.mapTo(HashSet()) { it.fileId }
                items = items + result.items.filter { shown.add(it.fileId) }
                searched = term
                next = result.nextPage ?: 0
                done = result.nextPage == null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    fun go(to: String) {
        if (to == side) return
        side = to
        load(true)
    }

    /** Hide (on "Visible") or bring back (on "Hidden"); the pictures leave the list at once. */
    fun mark(chosen: List<Item>, onMarked: (SensitiveMarks) -> Unit, onError: (String) -> Unit) {
        if (chosen.isEmpty() || busy) return
        val on = side == "visible"
        busy = true
        viewModelScope.launch {
            try {
                val res = api.markSensitive(chosen.associate { it.fileId.toString() to on })
                val gone = chosen.mapTo(HashSet()) { it.fileId }
                items = items.filterNot { it.fileId in gone }
                next = maxOf(0, next - chosen.size)
                marked = true
                onMarked(res)
                scheduleTrain()
                loadStatus()
                if (items.isEmpty() && !done) load(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    /** Undo a mark: back as it was, the list loads again. */
    fun undo(previous: Map<String, Boolean?>, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                api.markSensitive(previous)
                load(true)
                scheduleTrain()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun scheduleTrain() {
        timer?.cancel()
        timer = viewModelScope.launch {
            delay(TRAIN_DELAY)
            timer = null
            train()
        }
    }

    /** The server trains only when asked: marking stays instant. One at a time, again if marked meanwhile. */
    private suspend fun train() {
        if (training) {
            trainAgain = true
            return
        }
        training = true
        try {
            status = api.rebuildSensitive()
            trained = true
            resort = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        } finally {
            training = false
        }
        if (trainAgain) {
            trainAgain = false
            train()
        }
    }

    override fun onCleared() {
        // Marked just before leaving: still learn from it
        if (timer != null || training || trainAgain) {
            timer?.cancel()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { api.rebuildSensitive() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitiveTrainer() {
    val app = LocalApp.current
    val vm: TrainerVm = viewModel { TrainerVm(app.api) }
    val res = LocalContext.current.resources
    val selection = remember { Selection() }
    val gridState = rememberLazyGridState()
    val failed = stringResource(R.string.action_failed, "%s")
    val undoLabel = stringResource(R.string.undo)
    val items = vm.items.filterNot { it.fileId in app.deleted }
    val sections = remember(items) { if (items.isEmpty()) emptyList() else listOf(Section("all", "", items)) }
    val status = vm.status
    val model = status?.model == true
    val minPositive = status?.minPositive ?: 20

    // Leaving after marking: every other list loads again with the new filter
    val latestVm by rememberUpdatedState(vm)
    DisposableEffect(Unit) {
        onDispose {
            if (latestVm.marked) app.sensitive?.let { app.sensitiveModeChanged(it) }
        }
    }
    BackHandler(enabled = selection.active) { selection.clear() }

    fun act() {
        if (vm.busy) return
        val chosen = items.filter { it.fileId in selection.ids }
        val on = vm.side == "visible"
        selection.clear()
        vm.mark(
            chosen,
            onMarked = { marks ->
                app.sensitiveMarks = app.sensitiveMarks + marks.sensitive.mapKeys { it.key.toLong() }
                val text = res.getQuantityString(if (on) R.plurals.marked_sensitive else R.plurals.marked_not_sensitive, chosen.size, chosen.size)
                app.notices.show(text, undoLabel) {
                    vm.undo(marks.previous) { app.notices.show(failed.replace("%s", it)) }
                }
            },
            onError = { app.notices.show(failed.replace("%s", it)) },
        )
    }

    val orderLine = when {
        vm.side == "hidden" -> if (model) stringResource(R.string.trainer_order_harmless) else null
        vm.query.trim().isNotEmpty() && vm.searched.isNotEmpty() -> stringResource(R.string.trainer_order_closest, vm.searched)
        model -> stringResource(R.string.trainer_order_sensitive)
        else -> null
    }
    val empty = when {
        vm.side == "hidden" -> if (model) stringResource(R.string.trainer_empty_hidden) else null
        vm.searched.isNotEmpty() -> stringResource(R.string.trainer_empty_term)
        model -> stringResource(R.string.trainer_empty_visible)
        else -> null
    }

    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        PhotoGrid(
            sections = sections,
            selection = selection,
            onOpen = { app.open(items, it, gridKey = "sensitive-trainer") },
            state = gridState,
            loading = vm.loading,
            error = vm.error,
            onRetry = { vm.load(vm.items.isEmpty()) },
            hasMore = !vm.done && vm.error == null,
            onLoadMore = { vm.load(false) },
            contentPadding = PaddingValues(bottom = navBar + if (selection.active) 96.dp else 16.dp),
            columnsKey = "trainer",
            gridKey = "sensitive-trainer",
            headerCount = 1,
            empty = if (vm.done) empty else null,
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }, contentType = "top") {
                Column(Modifier.statusBarsPadding().padding(bottom = 12.dp)) {
                    IconButton(onClick = app.back, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = glimpse.text)
                    }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.trainer_title), style = MaterialTheme.typography.displaySmall, color = glimpse.text)
                        Spacer(Modifier.height(12.dp))
                        val sides = listOf("visible" to stringResource(R.string.trainer_visible), "hidden" to stringResource(R.string.trainer_hidden))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            sides.forEachIndexed { index, (id, label) ->
                                SegmentedButton(
                                    selected = vm.side == id,
                                    onClick = { selection.clear(); vm.go(id) },
                                    shape = SegmentedButtonDefaults.itemShape(index, sides.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = glimpse.accent,
                                        activeContentColor = Color(0xFF14162B),
                                    ),
                                ) { Text(label) }
                            }
                        }
                        orderLine?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = glimpse.text)
                        }
                        if (status != null) {
                            Spacer(Modifier.height(6.dp))
                            if (vm.training) {
                                Text(stringResource(R.string.trainer_learning), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = glimpse.text)
                            } else if (vm.trained && status.change != null) {
                                Text(
                                    stringResource(R.string.trainer_learned, status.change.added, status.change.removed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = glimpse.text,
                                )
                            }
                            val learned = pluralStringResource(R.plurals.trainer_hides, status.flagged, status.flagged) +
                                if (model) " " + stringResource(R.string.trainer_learns, status.learned.positives, status.learned.negatives) else ""
                            Text(learned, style = MaterialTheme.typography.bodyMedium, color = glimpse.muted)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (vm.side == "visible") {
                            Text(
                                stringResource(if (model) R.string.trainer_hint_visible else R.string.trainer_hint_visible_start),
                                style = MaterialTheme.typography.bodyMedium,
                                color = glimpse.text,
                            )
                            Spacer(Modifier.height(10.dp))
                            PillField(
                                value = vm.query,
                                onChange = { vm.query = it },
                                hint = stringResource(R.string.trainer_search_hint),
                                leading = Icons.Outlined.Search,
                                onDone = { selection.clear(); vm.load(true) },
                            )
                            if (vm.query.isNotEmpty() || vm.searched.isNotEmpty()) {
                                Row {
                                    TextButton(onClick = { selection.clear(); vm.load(true) }) { Text(stringResource(R.string.trainer_search)) }
                                    if (vm.searched.isNotEmpty()) {
                                        TextButton(onClick = { selection.clear(); vm.query = ""; vm.load(true) }) { Text(stringResource(R.string.trainer_clear_term)) }
                                    }
                                }
                            }
                        } else {
                            Text(
                                if (model) {
                                    stringResource(R.string.trainer_hint_hidden)
                                } else {
                                    val left = (minPositive - (status?.learned?.positives ?: 0)).coerceAtLeast(0)
                                    pluralStringResource(R.plurals.trainer_hint_hidden_start, minPositive, minPositive, left)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = glimpse.text,
                            )
                        }
                        if (vm.resort && !vm.training) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { selection.clear(); vm.load(true) }) { Text(stringResource(R.string.trainer_sort_again)) }
                        }
                        if (items.isNotEmpty() && !selection.active) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.trainer_select_hint), style = MaterialTheme.typography.bodySmall, color = glimpse.muted)
                        }
                    }
                }
            }
        }
        StatusScrim()

        AnimatedVisibility(
            visible = selection.active,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .fillMaxWidth()
                    .background(glimpse.raised, RoundedCornerShape(28.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(R.plurals.selected_count, selection.ids.size, selection.ids.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = glimpse.text,
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = !vm.busy, onClick = { selection.ids = items.mapTo(HashSet()) { it.fileId } }) { Text(stringResource(R.string.trainer_select_all)) }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { act() },
                    enabled = !vm.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = glimpse.accent, contentColor = Color(0xFF14162B)),
                ) {
                    Text(stringResource(if (vm.side == "visible") R.string.trainer_hide else R.string.trainer_bring_back))
                }
            }
        }
    }
}
