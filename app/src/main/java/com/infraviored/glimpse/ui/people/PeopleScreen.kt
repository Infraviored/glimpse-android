package com.infraviored.glimpse.ui.people

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.infraviored.glimpse.ui.theme.glimpse
import java.text.Collator

class PeopleVm(private val api: Api) : ViewModel() {
    var people by mutableStateOf<List<Person>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        error = null
        viewModelScope.loadInto({ error = it }, { api.people() }) { people = it.people.filter { p -> !p.hidden } }
    }
}

/** Everyone Immich recognised: named people by how often they appear, the unnamed after. */
@Composable
fun PeopleScreen(bottomPadding: Dp) {
    val app = LocalApp.current
    val vm: PeopleVm = viewModel { PeopleVm(app.api) }
    var filter by rememberSaveable { mutableStateOf("") }
    var showUnnamed by rememberSaveable { mutableStateOf(false) }
    val people = vm.people
    val collator = remember { Collator.getInstance() }
    val named = remember(people, filter) {
        people.orEmpty().filter { it.name.isNotBlank() && (filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true)) }
            .let { list -> if (filter.isBlank()) list.sortedByDescending { it.count } else list.sortedWith(compareBy(collator) { it.name }) }
    }
    val unnamed = remember(people) { people.orEmpty().filter { it.name.isBlank() }.sortedByDescending { it.count } }

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
        items(named, key = { it.id }) { person -> PersonTile(person) { app.person(person.id) } }
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
            if (showUnnamed) items(unnamed, key = { it.id }) { person -> PersonTile(person) { app.person(person.id) } }
        }
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

@Composable
fun PersonTile(person: Person, onClick: () -> Unit) {
    val api = LocalApp.current.api
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = api.faceUrl(person.id),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(glimpse.raised)
                .then(if (person.favorite) Modifier.border(2.dp, glimpse.accent, CircleShape) else Modifier).photoBlur(),
        )
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
