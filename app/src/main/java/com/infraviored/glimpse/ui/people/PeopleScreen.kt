package com.infraviored.glimpse.ui.people

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.Person
import com.infraviored.glimpse.ui.count
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.loadInto
import com.infraviored.glimpse.ui.search.Chip
import com.infraviored.glimpse.ui.theme.Palette
import com.infraviored.glimpse.ui.theme.glimpse
import java.text.Collator

class PeopleVm(private val api: Api) : ViewModel() {
    /** Everyone, hidden people too (the screen decides what to show). */
    var people by mutableStateOf<List<Person>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var canManage by mutableStateOf(false)
        private set

    /** People chosen for merging, hiding, favoring. */
    var chosen by mutableStateOf<Set<String>>(emptySet())
    var merging by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        error = null
        viewModelScope.loadInto({ error = it }, { api.people() }) {
            people = it.people
            canManage = it.canManage
            chosen = chosen.filter { id -> it.people.any { p -> p.id == id } }.toSet()
        }
    }

    fun toggle(id: String) {
        chosen = if (id in chosen) chosen - id else chosen + id
    }

    fun chosenPeople(): List<Person> = people.orEmpty().filter { it.id in chosen }

    /** Favorite or hide the chosen people; the list follows at once, the server after. */
    fun update(ids: Set<String>, hidden: Boolean? = null, favorite: Boolean? = null, onError: (String) -> Unit) {
        val before = people
        people = people?.map { if (it.id in ids) it.copy(hidden = hidden ?: it.hidden, favorite = favorite ?: it.favorite) else it }
        viewModelScope.loadInto({ people = before; onError(it) }, { ids.forEach { api.updatePerson(it, hidden = hidden, favorite = favorite) } }) {}
    }

    fun merge(target: String, others: List<String>, name: String, onDone: () -> Unit, onError: (String) -> Unit) {
        busy = true
        viewModelScope.loadInto({ busy = false; onError(it) }, {
            api.mergePeople(target, others)
            val old = people.orEmpty().firstOrNull { it.id == target }?.name.orEmpty()
            if (name.trim().isNotEmpty() && name.trim() != old) api.updatePerson(target, name = name.trim())
        }) {
            busy = false
            merging = false
            chosen = emptySet()
            onDone()
            load()
        }
    }
}

/** Favorites first, then by how often someone appears; filtered by name in the collator's order. */
fun sortPeople(people: List<Person>, filter: String, showHidden: Boolean, collator: Collator): List<Person> {
    val f = filter.trim()
    val list = people.filter { it.name.isNotBlank() && (showHidden || !it.hidden) && (f.isEmpty() || it.name.contains(f, ignoreCase = true)) }
    return if (f.isEmpty()) {
        list.sortedWith(compareByDescending<Person> { it.favorite }.thenByDescending { it.count })
    } else {
        list.sortedWith(compareBy(collator) { it.name })
    }
}

/** Everyone Immich recognised: named people by how often they appear, the unnamed after. */
@Composable
fun PeopleScreen(bottomPadding: Dp) {
    val app = LocalApp.current
    val vm: PeopleVm = viewModel { PeopleVm(app.api) }
    var filter by rememberSaveable { mutableStateOf("") }
    var showUnnamed by rememberSaveable { mutableStateOf(false) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val people = vm.people
    val collator = remember { Collator.getInstance() }
    val named = remember(people, filter, showHidden) { sortPeople(people.orEmpty(), filter, showHidden, collator) }
    val unnamed = remember(people, showHidden) { people.orEmpty().filter { it.name.isBlank() && (showHidden || !it.hidden) }.sortedByDescending { it.count } }
    val hiddenCount = people.orEmpty().count { it.hidden }
    val selecting = vm.chosen.isNotEmpty()

    // Renamed or merged elsewhere (a person's own page): load again
    val version = app.version
    LaunchedEffect(version) { if (version > 0) vm.load() }

    val onTile = { person: Person -> if (selecting) vm.toggle(person.id) else app.person(person.id) }
    val onLong = { person: Person -> if (vm.canManage) vm.toggle(person.id) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "head") {
            Column(Modifier.statusBarsPadding().padding(start = 4.dp, top = 8.dp)) {
                Text(stringResource(R.string.tab_people), style = MaterialTheme.typography.displaySmall, color = glimpse.text)
                Spacer(Modifier.height(14.dp))
                FilterField(filter, { filter = it })
                if (hiddenCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    Chip(pluralStringResource(R.plurals.people_hidden_count, hiddenCount, hiddenCount), selected = showHidden) { showHidden = !showHidden }
                }
            }
        }
        when {
            people == null && vm.error == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = glimpse.muted)
                }
            }
            vm.error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.load_failed, vm.error!!), color = glimpse.muted)
                    TextButton(onClick = vm::load) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        items(named, key = { it.id }) { person ->
            PersonTile(person, chosen = person.id in vm.chosen, selecting = selecting, onLongClick = { onLong(person) }) { onTile(person) }
        }
        if (people != null && named.isEmpty() && filter.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.people_no_match, filter), color = glimpse.muted, modifier = Modifier.padding(8.dp))
            }
        }
        if (unnamed.isNotEmpty() && filter.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "unnamed") {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.people_unnamed),
                        style = MaterialTheme.typography.titleLarge,
                        color = glimpse.text,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    TextButton(onClick = { showUnnamed = !showUnnamed }) {
                        Text(if (showUnnamed) stringResource(R.string.hide) else pluralStringResource(R.plurals.people_show_unnamed, unnamed.size, unnamed.size))
                    }
                }
            }
            if (showUnnamed) {
                items(unnamed, key = { it.id }) { person ->
                    PersonTile(person, chosen = person.id in vm.chosen, selecting = selecting, onLongClick = { onLong(person) }) { onTile(person) }
                }
            }
        }
    }

    if (vm.merging) {
        MergeSheet(
            people = vm.chosenPeople(),
            busy = vm.busy,
            onDismiss = { vm.merging = false },
            onMerge = { target, others, name ->
                vm.merge(target, others, name, onDone = { app.changed() }, onError = { app.notices.show(it) })
            },
        )
    }
}

@Composable
private fun FilterField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(glimpse.raised)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = glimpse.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (value.isEmpty()) Text(stringResource(R.string.people_filter), color = glimpse.muted, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = glimpse.text),
                cursorBrush = SolidColor(glimpse.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonTile(
    person: Person,
    chosen: Boolean = false,
    selecting: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val api = LocalApp.current.api
    val inset by animateFloatAsState(if (chosen) 0.86f else 1f, label = "inset")
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (person.hidden) 0.5f else 1f)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth(0.86f).aspectRatio(1f)) {
            AsyncImage(
                model = api.faceUrl(person.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(inset)
                    .clip(CircleShape)
                    .background(glimpse.raised)
                    .then(if (person.favorite || chosen) Modifier.border(2.dp, glimpse.accent, CircleShape) else Modifier).photoBlur(),
            )
            if (selecting) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .size(24.dp)
                        .then(
                            if (chosen) Modifier.background(glimpse.accent, CircleShape)
                            else Modifier.border(2.dp, glimpse.text.copy(alpha = 0.8f), CircleShape).background(glimpse.background.copy(alpha = 0.4f), CircleShape),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) Icon(Icons.Filled.Check, null, tint = Palette.Night, modifier = Modifier.size(16.dp))
                }
            }
            if (person.hidden) {
                Icon(
                    Icons.Outlined.VisibilityOff, stringResource(R.string.person_hidden),
                    tint = glimpse.text,
                    modifier = Modifier.align(Alignment.BottomEnd).size(26.dp).background(glimpse.raised, CircleShape).padding(5.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            person.name.ifBlank { stringResource(R.string.unnamed) },
            style = MaterialTheme.typography.titleMedium,
            color = if (person.name.isBlank()) glimpse.muted else glimpse.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            pluralStringResource(R.plurals.pictures, person.count, count(person.count)),
            style = MaterialTheme.typography.bodySmall,
            color = glimpse.muted,
        )
    }
}
