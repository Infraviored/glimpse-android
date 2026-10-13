package com.infraviored.glimpse.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ZoomIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.Marker
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.components.GlimpseSheet
import com.infraviored.glimpse.ui.components.SheetTitle
import com.infraviored.glimpse.ui.components.Thumbnail
import com.infraviored.glimpse.ui.count
import com.infraviored.glimpse.ui.loadInto
import com.infraviored.glimpse.ui.longDate
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.theme.Palette
import com.infraviored.glimpse.ui.theme.glimpse
import kotlin.math.roundToInt

class MapVm(private val api: Api) : ViewModel() {
    var markers by mutableStateOf<List<Marker>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Kept while switching tabs; null until the first fit to the pictures. */
    var view by mutableStateOf<Viewport?>(null)

    init {
        load()
    }

    fun load() {
        error = null
        viewModelScope.loadInto({ error = it }, { api.mapMarkers() }) { markers = it.markers }
    }

    fun reload() {
        viewModelScope.loadInto({}, { api.mapMarkers() }) { markers = it.markers }
    }
}

/** Where the pictures were taken: pins of their thumbnails, close ones together. */
@Composable
fun MapScreen(bottomPadding: Dp) {
    val app = LocalApp.current
    val vm: MapVm = viewModel { MapVm(app.api) }
    val density = LocalDensity.current
    var shown by remember { mutableStateOf<List<Marker>?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val privateVersion = app.privateVersion
    LaunchedEffect(privateVersion) { if (privateVersion > 0) vm.reload() }
    val markers = vm.markers.orEmpty().filterNot { it.fileId in app.deleted }
    val tint = mapTint(glimpse.isDark)

    BoxWithConstraints(Modifier.fillMaxSize().background(if (glimpse.isDark) Palette.Night else Palette.Paper)) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        viewSize = IntSize(w, h)
        LaunchedEffect(vm.markers, w, h) {
            if (vm.view == null && vm.markers != null && w > 0) {
                vm.view = Viewport.fit(Clusters.home(vm.markers!!), w, h, padPx = with(density) { 48.dp.roundToPx() })
            }
        }
        val view = vm.view ?: Viewport(Mercator.x(11.5), Mercator.y(48.1), 4.0)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(w, h) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val v = vm.view ?: view
                        vm.view = v.panBy(pan.x, pan.y).let { if (zoom != 1f) it.zoomBy(zoom, centroid.x, centroid.y, w, h) else it }
                    }
                }
                .pointerInput(w, h) {
                    detectTapGestures(onDoubleTap = { p ->
                        vm.view = (vm.view ?: view).zoomBy(2f, p.x, p.y, w, h)
                    })
                },
        ) {
            // Not before the pictures are known: the first view would only be thrown away
            if (vm.view != null || vm.markers?.isEmpty() == true || vm.error != null) Tiles(view, w, h, tint)
            Pins(markers, view, w, h, bottomPadding) { cluster ->
                if (cluster.size == 1) {
                    val item = cluster.markers.first().toItem()
                    app.open(listOf(item), item)
                } else {
                    shown = cluster.markers.sortedByDescending { it.time }
                }
            }
        }

        // Title over a fade, like the other tabs
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(glimpse.background, glimpse.background.copy(alpha = 0.85f), Color.Transparent)))
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        ) {
            Text(stringResource(R.string.tab_map), style = MaterialTheme.typography.displaySmall, color = glimpse.text)
        }

        val inView = remember(markers, view, w, h) { if (w > 0) Clusters.inView(markers, view, w, h) else emptyList() }
        Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            when {
                vm.error != null -> Pill(stringResource(R.string.load_failed, vm.error!!)) { vm.load() }
                vm.markers == null -> Box(Modifier.size(40.dp).clip(CircleShape).background(glimpse.raised), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = glimpse.muted)
                }
                markers.isEmpty() -> Pill(stringResource(R.string.map_empty)) {}
                inView.isNotEmpty() -> Pill(pluralStringResource(R.plurals.map_here, inView.size, count(inView.size))) { shown = inView }
            }
        }

        Text(
            "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall,
            color = glimpse.muted,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = bottomPadding + 2.dp),
        )
    }

    shown?.let { list ->
        PlaceSheet(
            markers = list,
            onZoom = {
                vm.view = Viewport.fit(list, viewSize.width, viewSize.height, padPx = with(density) { 72.dp.roundToPx() }, maxZoom = 18.0)
                shown = null
            },
            onDismiss = { shown = null },
        )
    }
}

/** The tiles at the zoom they are fetched in, scaled to the fractional zoom shown. */
@Composable
private fun Tiles(view: Viewport, w: Int, h: Int, tint: ColorFilter) {
    if (w == 0 || h == 0) return
    val api = LocalApp.current.api
    val density = LocalDensity.current
    val tiles = view.tiles(w, h)
    val z = view.tileZoom()
    val n = 1 shl z
    val sizePx = Mercator.worldPx(view.zoom) / n
    val sizeDp = with(density) { (sizePx + 1).toFloat().toDp() }
    tiles.forEach { t ->
        val x = view.toScreenX(t.x.toDouble() / n, w)
        val y = view.toScreenY(t.y.toDouble() / n, h)
        AsyncImage(
            model = api.tileUrl(t.z, t.x, t.y),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = tint,
            modifier = Modifier
                .absoluteOffset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .requiredSize(sizeDp),
        )
    }
}

/** Clusters of the current integer zoom, as thumbnails with how many are behind them. */
@Composable
private fun Pins(markers: List<Marker>, view: Viewport, w: Int, h: Int, bottomPadding: Dp, onTap: (Cluster) -> Unit) {
    if (w == 0 || markers.isEmpty()) return
    val api = LocalApp.current.api
    val density = LocalDensity.current
    val z = view.zoom.toInt()
    val pin = 54.dp
    val half = with(density) { (pin / 2).roundToPx() }
    // One cell a little larger than a pin: neighbours touch at most
    val cell = with(density) { (pin + 10.dp).roundToPx() }
    val clusters = remember(markers, z, cell) { Clusters.of(markers, z, cell) }
    val bottom = h - with(density) { bottomPadding.roundToPx() }
    clusters.forEach { c ->
        val x = view.toScreenX(c.x, w).roundToInt()
        val y = view.toScreenY(c.y, h).roundToInt()
        if (x < -half || x > w + half || y < -half || y > bottom + half) return@forEach
        Box(
            Modifier
                .absoluteOffset { IntOffset(x - half, y - half) }
                .size(pin),
        ) {
            AsyncImage(
                model = api.thumbnailUrl(c.cover.toItem()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(glimpse.raised)
                    .border(2.dp, Palette.Fog, RoundedCornerShape(16.dp))
                    .clickable { onTap(c) }
                    .photoBlur(),
            )
            if (c.size > 1) {
                Text(
                    if (c.size > 999) "999+" else c.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = glimpse.text,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .absoluteOffset(x = 8.dp, y = (-8).dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(glimpse.raised)
                        .border(1.dp, glimpse.line, RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = glimpse.text,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(glimpse.raised)
            .border(1.dp, glimpse.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** The pictures of a pin (or of the whole view), newest first. */
@Composable
private fun PlaceSheet(markers: List<Marker>, onZoom: () -> Unit, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val items = remember(markers) { markers.map { it.toItem() } }
    val first = items.lastOrNull()?.taken
    val last = items.firstOrNull()?.taken
    val range = when {
        first == null || last == null -> null
        first.toLocalDate() == last.toLocalDate() -> longDate(first)
        else -> "${longDate(first)} – ${longDate(last)}"
    }
    GlimpseSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SheetTitle(pluralStringResource(R.plurals.pictures, items.size, count(items.size)), range)
            }
            TextButton(onClick = onZoom) {
                Icon(Icons.Outlined.ZoomIn, null, tint = glimpse.accentText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.map_zoom_in), color = glimpse.accentText)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(14.dp)),
        ) {
            items(items, key = { it.fileId }) { item ->
                Thumbnail(item, selected = false, selecting = false, onClick = { app.open(items, item) }, onLongClick = {})
            }
        }
    }
}

/**
 * OpenStreetMap's colours would shout next to the photos: the map is turned into the app's
 * own two tones, night blue to mist (or paper to slate in the light theme), by lightness.
 */
fun mapTint(dark: Boolean): ColorFilter {
    val (lo, hi) = if (dark) Palette.Night to Palette.Mist else Palette.Slate to Palette.Paper
    val r = 0.299f; val g = 0.587f; val b = 0.114f
    val rows = FloatArray(20)
    listOf(lo.red to hi.red, lo.green to hi.green, lo.blue to hi.blue).forEachIndexed { i, (l, hh) ->
        val d = hh - l
        if (dark) {
            // light land becomes dark: out = hi - d * lightness
            rows[i * 5] = -d * r; rows[i * 5 + 1] = -d * g; rows[i * 5 + 2] = -d * b; rows[i * 5 + 4] = hh * 255f
        } else {
            rows[i * 5] = d * r; rows[i * 5 + 1] = d * g; rows[i * 5 + 2] = d * b; rows[i * 5 + 4] = l * 255f
        }
    }
    rows[18] = 1f
    return ColorFilter.colorMatrix(ColorMatrix(rows))
}

