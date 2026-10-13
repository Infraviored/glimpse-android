package com.infraviored.glimpse.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/** One geotagged picture: Glimpse's map index sends `[assetId, fileId, lat, lon, video, timestamp, path]`. */
data class Marker(
    val assetId: String,
    val fileId: Long,
    val lat: Double,
    val lon: Double,
    val video: Boolean,
    /** Capture time as Immich keeps it (local time read as UTC), seconds. */
    val time: Long,
    val path: String,
) {
    /** Position in Web Mercator world units (0..1), worked out once: panning reads them every frame. */
    val wx: Double = com.infraviored.glimpse.ui.map.Mercator.x(lon)
    val wy: Double = com.infraviored.glimpse.ui.map.Mercator.y(lat)

    /** As an item of the lists, without asking the server again. */
    fun toItem(): Item = Item(
        id = assetId,
        fileId = fileId,
        type = if (video) "VIDEO" else "IMAGE",
        date = if (time > 0) Instant.ofEpochSecond(time).toString() else null,
        name = path.substringAfterLast('/'),
        path = path,
    )
}

data class MapData(val updated: Long?, val markers: List<Marker>) {
    companion object {
        fun parse(root: JsonElement): MapData {
            val obj = root.jsonObject
            val updated = (obj["updated"] as? JsonPrimitive)?.longOrNull
            val markers = (obj["markers"] as? JsonArray).orEmpty().mapNotNull { row ->
                val a = row as? JsonArray ?: return@mapNotNull null
                val p = { i: Int -> a.getOrNull(i) as? JsonPrimitive }
                val asset = p(0)?.contentOrNull ?: return@mapNotNull null
                val fileId = p(1)?.longOrNull ?: return@mapNotNull null
                val lat = p(2)?.doubleOrNull ?: return@mapNotNull null
                val lon = p(3)?.doubleOrNull ?: return@mapNotNull null
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                Marker(
                    assetId = asset,
                    fileId = fileId,
                    lat = lat,
                    lon = lon,
                    video = (p(4)?.longOrNull ?: 0L) != 0L,
                    time = p(5)?.longOrNull ?: 0L,
                    path = p(6)?.contentOrNull.orEmpty(),
                )
            }
            return MapData(updated, markers)
        }
    }
}
