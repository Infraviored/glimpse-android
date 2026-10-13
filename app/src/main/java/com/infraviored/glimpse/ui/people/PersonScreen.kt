package com.infraviored.glimpse.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.FaceTarget
import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.data.Person
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.Paged
import com.infraviored.glimpse.ui.byMonth
import com.infraviored.glimpse.ui.components.DockAction
import com.infraviored.glimpse.ui.components.PhotoGrid
import com.infraviored.glimpse.ui.components.Selection
import com.infraviored.glimpse.ui.components.SelectionScaffold
import com.infraviored.glimpse.ui.count
import com.infraviored.glimpse.ui.loadInto
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.theme.Palette
import com.infraviored.glimpse.ui.theme.glimpse

class PersonVm(private val api: Api, val id: String) : ViewModel() {
    val paged = Paged(viewModelScope) { page -> api.search(person = id, page = page) }
    var person by mutableStateOf<Person?>(null)
        private set

    /** Everyone else, for "Not this person". */
    var others by mutableStateOf<List<Person>>(emptyList())
        private set
    var canManage by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    init {
        paged.loadMore()
        loadPerson()
    }

    fun loadPerson() {
        viewModelScope.loadInto({}, { api.people() }) { result ->
            person = result.people.firstOrNull { it.id == id }
            others = result.people.filter { it.id != id }
            canManage = result.canManage
        }
    }

    /** Name, hidden, favorite; the header follows at once and goes back if the server says no. */
    fun update(name: String? = null, hidden: Boolean? = null, favorite: Boolean? = null, onDone: () -> Unit, onError: (String) -> Unit) {
        val before = person ?: return
        person = before.copy(name = name?.trim() ?: before.name, hidden = hidden ?: before.hidden, favorite = favorite ?: before.favorite)
        viewModelScope.loadInto({ person = before; onError(it) }, { api.updatePerson(id, name, hidden, favorite) }) { onDone() }
    }

    fun notThisPerson(items: List<Item>, target: FaceTarget, onDone: (Int) -> Unit, onError: (String) -> Unit) {
        val assets = items.mapNotNull { it.id }
        if (assets.isEmpty()) return
        busy = true
        viewModelScope.loadInto({ busy = false; onError(it) }, { api.moveFaces(id, assets, target) }) { result ->
            busy = false
            val moved = result.moved.toSet()
            paged.remove(items.filter { it.id in moved }.map { it.fileId }.toSet())
            if (moved.isEmpty()) {
                onError(if (result.failed.isNotEmpty()) "Failed" else "No faces moved")
            } else {
                onDone(moved.size)
                loadPerson()
            }
        }
    }
}

/** One person: their face, name and every picture they are in, newest first. */
@Composable
fun PersonScreen(id: String) {
    val app = LocalApp.current
    val context = LocalContext.current
    val vm: PersonVm = viewModel(key = "person-$id") { PersonVm(app.api, id) }
    val paged = vm.paged
    val version = app.version
    val privateVersion = app.privateVersion
    LaunchedEffect(version, privateVersion) {
        if (version > 0 || privateVersion > 0) {
            vm.loadPerson()
            paged.reload()
        }
    }
    val unknown = stringResource(R.string.unknown_date)
    val items = paged.items.filterNot { it.fileId in app.deleted }
    val sections = remember(items) { byMonth(items, unknown) }
    val selection = remember { Selection() }
    val gridState = rememberLazyGridState()
    var notPerson by remember { mutableStateOf<List<Item>?>(null) }
    val person = vm.person
    val name = person?.name.orEmpty().ifBlank { stringResource(R.string.unnamed) }

    val extra = if (vm.canManage && person != null) {
        listOf(
            DockAction(Icons.Outlined.PersonRemove, stringResource(R.string.not_person_action, name), stringResource(R.string.not_person_action_hint)) {
                notPerson = items.filter { it.fileId in selection.ids && it.id != null }
            },
        )
    } else {
        emptyList()
    }

    SelectionScaffold(selection, items, extra) { bottom ->
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
            contentPadding = PaddingValues(bottom = bottom),
            columnsKey = "person",
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }, contentType = "top") {
                Column(Modifier.statusBarsPadding()) {
                    IconButton(onClick = app.back, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = glimpse.text)
                    }
                    PersonHeader(vm)
                }
            }
        }
    }

    val chosen = notPerson
    if (chosen != null && person != null) {
        NotPersonSheet(
            person = person,
            count = chosen.size,
            others = vm.others,
            busy = vm.busy,
            onDismiss = { notPerson = null },
            onApply = { target ->
                vm.notThisPerson(chosen, target, onDone = { n ->
                    notPerson = null
                    selection.clear()
                    app.notices.show(context.resources.getQuantityString(R.plurals.not_person_done, n, n))
                    app.changed()
                }, onError = { app.notices.show(context.getString(R.string.action_failed, it)) })
            },
        )
    }
}

@Composable
private fun PersonHeader(vm: PersonVm) {
    val app = LocalApp.current
    val context = LocalContext.current
    val person = vm.person
    var editing by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.action_failed, "%s")
    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = app.api.faceUrl(vm.id),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(glimpse.raised)
                .photoBlur(),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val name = person?.name.orEmpty()
            if (editing && person != null) {
                NameEditor(
                    initial = name,
                    onCancel = { editing = false },
                    onSave = { new ->
                        editing = false
                        if (new.trim() != name) {
                            vm.update(name = new, onDone = { app.changed() }, onError = { app.notices.show(failed.replace("%s", it)) })
                        }
                    },
                )
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = vm.canManage && person != null) { editing = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name.ifBlank { stringResource(if (vm.canManage) R.string.person_add_name else R.string.unnamed) },
                        style = MaterialTheme.typography.displayMedium,
                        color = if (name.isBlank()) glimpse.muted else glimpse.text,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (vm.canManage && person != null) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.Edit, stringResource(R.string.person_rename), tint = glimpse.muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
            person?.let {
                Text(pluralStringResource(R.plurals.pictures, it.count, count(it.count)), color = glimpse.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (person != null && vm.canManage && !editing) {
        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(
                icon = if (person.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                label = stringResource(if (person.favorite) R.string.person_favorite_on else R.string.person_favorite),
                on = person.favorite,
            ) { vm.update(favorite = !person.favorite, onDone = { app.changed() }, onError = { app.notices.show(failed.replace("%s", it)) }) }
            Toggle(
                icon = if (person.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                label = stringResource(if (person.hidden) R.string.person_hidden else R.string.person_hide),
                on = person.hidden,
            ) {
                val hide = !person.hidden
                vm.update(hidden = hide, onDone = {
                    app.changed()
                    if (hide) app.notices.show(context.getString(R.string.person_hidden_notice))
                }, onError = { app.notices.show(failed.replace("%s", it)) })
            }
        }
    }
}

/** The name in place, in the same big type, with save and cancel. */
@Composable
private fun NameEditor(initial: String, onCancel: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler { onCancel() }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.text.isEmpty()) {
                    Text(stringResource(R.string.person_name), style = MaterialTheme.typography.displaySmall, color = glimpse.muted)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.displaySmall.copy(color = glimpse.text),
                    cursorBrush = SolidColor(glimpse.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(value.text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel), tint = glimpse.muted) }
            IconButton(onClick = { onSave(value.text) }) {
                Icon(
                    Icons.Outlined.Check, stringResource(R.string.save), tint = Palette.Night,
                    modifier = Modifier.size(34.dp).background(glimpse.accent, CircleShape).padding(6.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(glimpse.accent))
    }
}

/** A small pill that is apricot while on. */
@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) glimpse.accent else glimpse.raised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) Palette.Night else glimpse.text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Night else glimpse.text)
    }
}
