package com.infraviored.glimpse.ui.search

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.Person
import com.infraviored.glimpse.data.Tag
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.Paged
import com.infraviored.glimpse.ui.Section
import com.infraviored.glimpse.ui.components.LensMark
import com.infraviored.glimpse.ui.components.PhotoGrid
import com.infraviored.glimpse.ui.components.Selection
import com.infraviored.glimpse.ui.rememberPhotoActions
import com.infraviored.glimpse.ui.components.DockAction
import com.infraviored.glimpse.ui.components.SelectionScaffold
import com.infraviored.glimpse.ui.loadInto
import com.infraviored.glimpse.ui.theme.glimpse

/** Kinds of media the results can be narrowed to; the value is Glimpse's "type". */
enum class Kind(val value: String, val label: Int) {
    All("", R.string.kind_all),
    Photos("IMAGE", R.string.kind_photos),
    Videos("VIDEO", R.string.kind_videos),
}

class SearchVm(private val api: Api, private val context: Context, q: String, personId: String, val similar: String) : ViewModel() {
    var query by mutableStateOf(q)
    /** The query the results are for (typing alone does not search). */
    var submitted by mutableStateOf(q)
        private set
    var person by mutableStateOf<Person?>(null)
    var tag by mutableStateOf<Tag?>(null)
    var kind by mutableStateOf(Kind.All)
    var people by mutableStateOf<List<Person>>(emptyList())
        private set
    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set
    var recent by mutableStateOf(loadRecent())
        private set
    private val initialPerson = personId

    val paged = Paged(viewModelScope) { page, seen ->
        api.search(q = submitted, page = page, person = person?.id.orEmpty(), type = kind.value, similar = similar, tag = tag?.id?.toString().orEmpty(), seen = seen)
    }

    val active: Boolean get() = submitted.isNotBlank() || person != null || tag != null || similar.isNotEmpty()

    init {
        viewModelScope.loadInto({}, { api.people() }) { result ->
            people = result.people.filter { it.name.isNotBlank() && !it.hidden }.sortedByDescending { it.count }
            if (initialPerson.isNotEmpty()) {
                person = result.people.firstOrNull { it.id == initialPerson }
                run()
            }
        }
        viewModelScope.loadInto({}, { api.tags() }) { tags = it }
        if (active) run()
    }

    fun submit(text: String = query) {
        query = text
        submitted = text.trim()
        if (submitted.isNotEmpty()) remember(submitted)
        run()
    }

    fun run() {
        if (active) paged.reload()
    }

    private fun prefs() = context.getSharedPreferences("search", Context.MODE_PRIVATE)
    private fun loadRecent(): List<String> = prefs().getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() }
    private fun remember(q: String) {
        recent = (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(8)
        prefs().edit().putString("recent", recent.joinToString("\n")).apply()
    }
    fun forget(q: String) {
        recent = recent - q
        prefs().edit().putString("recent", recent.joinToString("\n")).apply()
    }
}

/**
 * The open lens: one big field for what should be in the picture, people and earlier
 * searches below it. Results replace the suggestions as a grid, best match first.
 */
@Composable
fun SearchScreen(q: String, personId: String, similar: String, label: String) {
    val app = LocalApp.current
    val context = LocalContext.current
    val vm: SearchVm = viewModel(key = "search-$q-$personId-$similar") { SearchVm(app.api, context.applicationContext, q, personId, similar) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val selection = remember { Selection() }
    val gridState = rememberLazyGridState()
    val items = vm.paged.items.filterNot { it.fileId in app.deleted }
    val sections = remember(items) { if (items.isEmpty()) emptyList() else listOf(Section("results", "", items)) }

    LaunchedEffect(Unit) { if (!vm.active) focus.requestFocus() }

    // The words the results are ranked by (a person the server recognised in them taken out)
    val term = if (similar.isEmpty()) (vm.paged.lastPage?.person?.rest ?: vm.submitted).trim() else ""
    val actions = rememberPhotoActions()
    val single = items.singleOrNull { it.fileId in selection.ids }?.takeIf { selection.ids.size == 1 }
    val extra = if (app.sensitive != null && term.isNotEmpty()) {
        listOf(
            DockAction(
                com.infraviored.glimpse.ui.SensitiveIcon,
                stringResource(R.string.sensitive_up_to_here),
                subtitle = if (single == null) stringResource(R.string.sensitive_up_to_here_one) else null,
                enabled = single != null,
            ) { single?.let { actions.sensitiveUpToHere(term, it, items) { selection.clear() } } },
        )
    } else {
        emptyList()
    }
    SelectionScaffold(selection, items, extra) { bottom ->
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // The field
            Row(
                Modifier
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = app.back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = glimpse.text) }
                if (similar.isNotEmpty()) {
                    Text(stringResource(R.string.search_similar_to, label), style = MaterialTheme.typography.headlineSmall, color = glimpse.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(27.dp))
                            .background(glimpse.raised)
                            .border(1.dp, glimpse.line, RoundedCornerShape(27.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LensMark(Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            if (vm.query.isEmpty()) Text(stringResource(R.string.lens_hint), color = glimpse.muted, style = MaterialTheme.typography.bodyLarge)
                            BasicTextField(
                                value = vm.query,
                                onValueChange = { vm.query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = glimpse.text),
                                cursorBrush = SolidColor(glimpse.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { vm.submit(); focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        }
                        if (vm.query.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.Close,
                                stringResource(R.string.search_clear),
                                tint = glimpse.muted,
                                modifier = Modifier.size(22.dp).clip(CircleShape).clickable { vm.query = ""; focus.requestFocus() },
                            )
                        }
                    }
                }
            }

            // Filters: kind, the person and tag chosen
            if (vm.active) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Kind.entries) { k ->
                        Chip(stringResource(k.label), selected = vm.kind == k) { vm.kind = k; vm.run() }
                    }
                    vm.person?.let { p -> item { Chip(p.name, selected = true, onClear = { vm.person = null; vm.run() }) {} } }
                    vm.tag?.let { t -> item { Chip("#${t.name}", selected = true, onClear = { vm.tag = null; vm.run() }) {} } }
                }
            }

            if (!vm.active) {
                Suggestions(vm, onPick = { focusManager.clearFocus() })
            } else {
                Box(Modifier.weight(1f)) {
                    PhotoGrid(
                        sections = sections,
                        selection = selection,
                        onOpen = { app.open(items, it, vm.paged, gridKey = "search") },
                        state = gridState,
                        loading = vm.paged.loading,
                        error = vm.paged.error,
                        onRetry = { vm.paged.loadMore() },
                        hasMore = vm.paged.hasMore,
                        onLoadMore = { vm.paged.loadMore() },
                        contentPadding = PaddingValues(bottom = bottom),
                        columnsKey = "search",
                        gridKey = "search",
                        headerCount = 0,
                    ) {
                        if (vm.paged.loaded && items.isEmpty() && !vm.paged.loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(Modifier.padding(24.dp)) {
                                    Text(stringResource(R.string.search_nothing), style = MaterialTheme.typography.titleLarge, color = glimpse.text)
                                    Spacer(Modifier.height(6.dp))
                                    Text(stringResource(R.string.search_nothing_hint), color = glimpse.muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Suggestions(vm: SearchVm, onPick: () -> Unit) {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        if (vm.people.isNotEmpty()) {
            SectionTitle(stringResource(R.string.tab_people))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(vm.people.take(24), key = { it.id }) { p ->
                    Column(
                        Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.person = p; onPick(); vm.run() }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = app.api.faceUrl(p.id),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(60.dp).clip(CircleShape).background(glimpse.raised).photoBlur(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(p.name, style = MaterialTheme.typography.bodySmall, color = glimpse.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (vm.recent.isNotEmpty()) {
            SectionTitle(stringResource(R.string.search_recent))
            vm.recent.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(); vm.submit(q) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.History, null, tint = glimpse.muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(q, color = glimpse.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Outlined.Close,
                        stringResource(R.string.search_forget),
                        tint = glimpse.muted,
                        modifier = Modifier.size(20.dp).clip(CircleShape).clickable { vm.forget(q) },
                    )
                }
            }
        }
        if (vm.tags.isNotEmpty()) {
            SectionTitle(stringResource(R.string.search_tags))
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.tags.forEach { t -> Chip("#${t.name}", selected = false) { vm.tag = t; onPick(); vm.run() } }
            }
        }
        if (vm.people.isEmpty() && vm.recent.isEmpty()) {
            Text(
                stringResource(R.string.search_intro),
                color = glimpse.muted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = glimpse.text, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp))
}

@Composable
fun Chip(label: String, selected: Boolean, onClear: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) glimpse.accent else glimpse.raised)
            .clickable(onClick = onClear ?: onClick)
            .padding(start = 14.dp, end = if (onClear != null) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) com.infraviored.glimpse.ui.theme.Palette.Night else glimpse.text)
        if (onClear != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.Close, stringResource(R.string.search_clear), tint = com.infraviored.glimpse.ui.theme.Palette.Night, modifier = Modifier.size(16.dp))
        }
    }
}

