package com.infraviored.glimpse.ui

import com.infraviored.glimpse.data.Marker
import com.infraviored.glimpse.ui.map.Clusters
import com.infraviored.glimpse.ui.map.Mercator
import com.infraviored.glimpse.ui.map.Tile
import com.infraviored.glimpse.ui.map.Viewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    private fun m(id: String, lat: Double, lon: Double, time: Long = 0) = Marker(id, id.hashCode().toLong(), lat, lon, false, time, "/$id.jpg")

    @Test
    fun mercatorRoundTrip() {
        assertEquals(0.5, Mercator.x(0.0), 1e-9)
        assertEquals(0.5, Mercator.y(0.0), 1e-9)
        assertEquals(48.137, Mercator.lat(Mercator.y(48.137)), 1e-6)
        assertEquals(11.575, Mercator.lon(Mercator.x(11.575)), 1e-6)
    }

    @Test
    fun munichIsInTheRightTile() {
        // OSM tile of Munich at zoom 10: 545/355
        val v = Viewport(Mercator.x(11.575), Mercator.y(48.137), 10.0)
        assertTrue(Tile(10, 545, 355) in v.tiles(256, 256))
    }

    @Test
    fun zoomKeepsThePointUnderTheFingers() {
        val v = Viewport(0.5, 0.5, 5.0)
        val wx = v.toWorldX(100.0, 400)
        val wy = v.toWorldY(300.0, 600)
        val z = v.zoomBy(2f, 100f, 300f, 400, 600)
        assertEquals(6.0, z.zoom, 1e-9)
        assertEquals(wx, z.toWorldX(100.0, 400), 1e-9)
        assertEquals(wy, z.toWorldY(300.0, 600), 1e-9)
    }

    @Test
    fun zoomStaysInRange() {
        assertEquals(Viewport.MAX_ZOOM, Viewport(0.5, 0.5, 18.5).zoomBy(8f, 0f, 0f, 100, 100).zoom, 1e-9)
        assertEquals(Viewport.MIN_ZOOM, Viewport(0.5, 0.5, 3.0).zoomBy(0.01f, 0f, 0f, 100, 100).zoom, 1e-9)
        assertEquals(18, Viewport(0.5, 0.5, 19.0).tileZoom())
    }

    @Test
    fun nearbyPicturesClusterFarOnesDoNot() {
        val list = listOf(m("a", 48.1371, 11.5754, 1), m("b", 48.1372, 11.5755, 5), m("c", 52.52, 13.40))
        val clusters = Clusters.of(list, 10)
        assertEquals(2, clusters.size)
        val munich = clusters.first { it.size == 2 }
        assertEquals("b", munich.cover.assetId)
        // close in, they come apart
        assertEquals(3, Clusters.of(list, 19).size)
    }

    @Test
    fun homeIsWhereMostPicturesAre() {
        val list = listOf(m("a", 48.1, 11.5), m("b", 48.2, 11.6), m("c", 47.9, 10.9), m("d", -33.9, 151.2))
        assertEquals(setOf("a", "b", "c"), Clusters.home(list).map { it.assetId }.toSet())
    }

    @Test
    fun inViewNewestFirst() {
        val list = listOf(m("a", 48.1, 11.5, 1), m("b", 48.11, 11.51, 9), m("c", 52.52, 13.40, 5))
        val v = Viewport(Mercator.x(11.5), Mercator.y(48.1), 12.0)
        assertEquals(listOf("b", "a"), Clusters.inView(list, v, 1000, 1000).map { it.assetId })
    }

    @Test
    fun fitShowsAllMarkers() {
        val list = listOf(m("a", 48.0, 11.0), m("b", 49.0, 12.0))
        val v = Viewport.fit(list, 1000, 1000, padPx = 0, maxZoom = 19.0)
        list.forEach {
            val x = v.toScreenX(Mercator.x(it.lon), 1000)
            val y = v.toScreenY(Mercator.y(it.lat), 1000)
            assertTrue(x in -0.5..1000.5 && y in -0.5..1000.5)
        }
    }
}
