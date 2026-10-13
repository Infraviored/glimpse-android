package com.infraviored.glimpse.ui.map

import com.infraviored.glimpse.data.Marker
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Web Mercator, as OpenStreetMap's tiles use it: the world is a square of `256 * 2^zoom`
 * pixels; here in "world units" from 0 to 1, so one position works at every zoom.
 */
object Mercator {
    const val TILE = 256
    const val MAX_LAT = 85.05112878

    fun x(lon: Double): Double = (lon + 180.0) / 360.0

    fun y(lat: Double): Double {
        val l = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180.0
        return (1.0 - ln(tan(l) + 1.0 / kotlin.math.cos(l)) / PI) / 2.0
    }

    fun lon(x: Double): Double = x * 360.0 - 180.0

    fun lat(y: Double): Double = atan(kotlin.math.sinh(PI * (1.0 - 2.0 * y))) * 180.0 / PI

    /** Size of the world in pixels at [zoom] (fractional zooms scale the tiles). */
    fun worldPx(zoom: Double): Double = TILE * Math.pow(2.0, zoom)
}

/** What the map shows: its centre in world units and the zoom. */
data class Viewport(val cx: Double, val cy: Double, val zoom: Double) {
    /** Screen pixel of a world position, for a view [w]×[h] pixels big. */
    fun toScreenX(x: Double, w: Int): Double = (x - cx) * Mercator.worldPx(zoom) + w / 2.0
    fun toScreenY(y: Double, h: Int): Double = (y - cy) * Mercator.worldPx(zoom) + h / 2.0

    fun toWorldX(px: Double, w: Int): Double = cx + (px - w / 2.0) / Mercator.worldPx(zoom)
    fun toWorldY(py: Double, h: Int): Double = cy + (py - h / 2.0) / Mercator.worldPx(zoom)

    /** Pan by a finger's movement in pixels. */
    fun panBy(dx: Float, dy: Float): Viewport {
        val s = Mercator.worldPx(zoom)
        return copy(cx = cx - dx / s, cy = cy - dy / s).clamped()
    }

    /** Zoom by [factor] keeping the point under ([px], [py]) where it is. */
    fun zoomBy(factor: Float, px: Float, py: Float, w: Int, h: Int): Viewport {
        val wx = toWorldX(px.toDouble(), w)
        val wy = toWorldY(py.toDouble(), h)
        val z = (zoom + ln(factor.toDouble()) / ln(2.0)).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val s = Mercator.worldPx(z)
        return Viewport(wx - (px - w / 2.0) / s, wy - (py - h / 2.0) / s, z).clamped()
    }

    /** The world stays on screen: no panning off into nothing. */
    fun clamped(): Viewport = copy(cx = cx.coerceIn(0.0, 1.0), cy = cy.coerceIn(0.0, 1.0), zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))

    /** Tiles covering a [w]×[h] view, at the integer zoom the tiles are fetched in. */
    fun tiles(w: Int, h: Int): List<Tile> {
        val z = tileZoom()
        val n = 1 shl z
        val x0 = floor(toWorldX(0.0, w) * n).toInt().coerceAtLeast(0)
        val x1 = floor(toWorldX(w.toDouble(), w) * n).toInt().coerceAtMost(n - 1)
        val y0 = floor(toWorldY(0.0, h) * n).toInt().coerceAtLeast(0)
        val y1 = floor(toWorldY(h.toDouble(), h) * n).toInt().coerceAtMost(n - 1)
        val out = ArrayList<Tile>()
        for (y in y0..y1) for (x in x0..x1) out += Tile(z, x, y)
        return out
    }

    /** The proxy serves tiles up to 18; beyond that they are scaled up. */
    fun tileZoom(): Int = floor(zoom + 0.25).toInt().coerceIn(0, MAX_TILE_ZOOM)

    companion object {
        const val MIN_ZOOM = 2.0
        const val MAX_ZOOM = 19.0
        const val MAX_TILE_ZOOM = 18

        /** The view that shows [markers]' box in a [w]×[h] view, at most at [maxZoom]. */
        fun fit(markers: List<Marker>, w: Int, h: Int, padPx: Int = 48, maxZoom: Double = 12.0): Viewport {
            if (markers.isEmpty()) return Viewport(Mercator.x(11.5), Mercator.y(48.1), 4.0)
            val xs = markers.map { it.wx }
            val ys = markers.map { it.wy }
            val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
            val spanX = (maxX - minX).coerceAtLeast(1e-9)
            val spanY = (maxY - minY).coerceAtLeast(1e-9)
            val availW = (w - 2 * padPx).coerceAtLeast(1)
            val availH = (h - 2 * padPx).coerceAtLeast(1)
            val zx = ln(availW / (spanX * Mercator.TILE)) / ln(2.0)
            val zy = ln(availH / (spanY * Mercator.TILE)) / ln(2.0)
            val z = minOf(zx, zy, maxZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
            return Viewport((minX + maxX) / 2, (minY + maxY) / 2, z)
        }
    }
}

data class Tile(val z: Int, val x: Int, val y: Int)

/** Pictures close together on screen, shown as one pin with the newest picture on it. */
data class Cluster(val x: Double, val y: Double, val markers: List<Marker>) {
    val cover: Marker get() = markers.maxBy { it.time }
    val size: Int get() = markers.size
    val key: String get() = cover.assetId
}

object Clusters {
    /**
     * Grid clustering in world space at the integer [zoom]: markers in the same cell of
     * [cellPx] pixels become one cluster at their mean position. Stable while panning (the
     * grid does not move with the view), so pins do not jump.
     */
    fun of(markers: List<Marker>, zoom: Int, cellPx: Int = 72): List<Cluster> {
        val cells = Mercator.worldPx(zoom.toDouble()) / cellPx
        val groups = HashMap<Long, MutableList<Marker>>()
        for (m in markers) {
            val cx = floor(m.wx * cells).toLong()
            val cy = floor(m.wy * cells).toLong()
            groups.getOrPut(cx * 1_000_003L + cy) { ArrayList() } += m
        }
        val grid = groups.values.map { list ->
            Cluster(list.sumOf { it.wx } / list.size, list.sumOf { it.wy } / list.size, list)
        }
        // Neighbouring cells can put two pins on top of each other: the bigger one takes in
        // whatever lies closer than a cell
        val minDist = cellPx / Mercator.worldPx(zoom.toDouble())
        val merged = ArrayList<MutableList<Marker>>()
        val centres = ArrayList<DoubleArray>()
        for (c in grid.sortedByDescending { it.size }) {
            val near = centres.indexOfFirst { kotlin.math.abs(it[0] - c.x) < minDist && kotlin.math.abs(it[1] - c.y) < minDist }
            if (near < 0) {
                merged += c.markers.toMutableList()
                centres += doubleArrayOf(c.x, c.y)
            } else {
                merged[near] += c.markers
            }
        }
        return merged.mapIndexed { i, list -> Cluster(centres[i][0], centres[i][1], list) }
    }

    /**
     * Where most pictures are: the densest 1° cell and what lies within ~2.5° of it. Fitting
     * every marker would show the whole world after a single holiday abroad.
     */
    fun home(markers: List<Marker>): List<Marker> {
        if (markers.isEmpty()) return markers
        val best = markers.groupingBy { floor(it.lat).toInt() to floor(it.lon).toInt() }.eachCount().maxBy { it.value }.key
        val lat = best.first + 0.5
        val lon = best.second + 0.5
        return markers.filter { kotlin.math.abs(it.lat - lat) <= 2.5 && kotlin.math.abs(it.lon - lon) <= 2.5 }
    }

    /** Markers inside the view, newest first. */
    fun inView(markers: List<Marker>, view: Viewport, w: Int, h: Int): List<Marker> {
        val x0 = view.toWorldX(0.0, w); val x1 = view.toWorldX(w.toDouble(), w)
        val y0 = view.toWorldY(0.0, h); val y1 = view.toWorldY(h.toDouble(), h)
        return markers.filter {
            it.wx in x0..x1 && it.wy in y0..y1
        }.sortedByDescending { it.time }
    }
}

