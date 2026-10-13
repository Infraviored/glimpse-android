package com.infraviored.glimpse.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.infraviored.glimpse.ui.Paged
import com.infraviored.glimpse.ui.byMonth
import com.infraviored.glimpse.ui.loadInto
import com.infraviored.glimpse.ui.components.PhotoGrid
import com.infraviored.glimpse.ui.components.Selection
import com.infraviored.glimpse.ui.components.SelectionScaffold
import com.infraviored.glimpse.ui.theme.glimpse

class PersonVm(private val api: Api, val id: String) : ViewModel() {
    val paged = Paged(viewModelScope) { page -> api.search(person = id, page = page) }
    var person by mutableStateOf<Person?>(null)
        private set

    init {
        paged.loadMore()
        viewModelScope.loadInto({}, { api.people() }) { result -> person = result.people.firstOrNull { it.id == id } }
    }
}

/** One person: their face, name and every picture they are in, newest first. */
@Composable
fun PersonScreen(id: String) {
    val app = LocalApp.current
    val vm: PersonVm = viewModel(key = "person-$id") { PersonVm(app.api, id) }
    val paged = vm.paged
    val unknown = stringResource(R.string.unknown_date)
    val items = paged.items.filterNot { it.fileId in app.deleted }
    val sections = remember(items) { byMonth(items, unknown) }
    val selection = remember { Selection() }
    val gridState = rememberLazyGridState()

    SelectionScaffold(selection, items) { bottom ->
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
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = app.api.faceUrl(id),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(84.dp).clip(CircleShape).background(glimpse.raised).photoBlur(),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            val name = vm.person?.name.orEmpty()
                            Text(
                                name.ifBlank { stringResource(R.string.unnamed) },
                                style = MaterialTheme.typography.displayMedium,
                                color = if (name.isBlank()) glimpse.muted else glimpse.text,
                                maxLines = 2,
                            )
                            vm.person?.let {
                                Text(pluralStringResource(R.plurals.pictures, it.count, count(it.count)), color = glimpse.muted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
