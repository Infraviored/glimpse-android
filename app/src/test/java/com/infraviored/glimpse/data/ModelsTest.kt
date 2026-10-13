package com.infraviored.glimpse.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test
    fun mapMarkersParse() {
        val data = MapData.parse(
            json.parseToJsonElement(
                """{"updated":1791844011,"markers":[
                ["a1",459344,48.37001,10.89476,0,1791749403,"\/Photos\/Cam\/20261011_201003.jpg"],
                ["a2",12,47.9,10.9,1,0],
                ["bad"],
                ["a3",13,95.0,10.0,0,0,""]
                ]}""",
            ),
        )
        assertEquals(1791844011L, data.updated)
        assertEquals(2, data.markers.size)
        val first = data.markers[0].toItem()
        assertEquals("a1", first.id)
        assertEquals(459344L, first.fileId)
        assertEquals("20261011_201003.jpg", first.name)
        assertEquals("/Photos/Cam/20261011_201003.jpg", first.path)
        assertEquals(2026, first.taken?.year)
        val video = data.markers[1].toItem()
        assertEquals(true, video.isVideo)
        assertEquals("a2", video.name)
        assertNull(video.date)
    }

    @Test
    fun emptyMap() {
        assertEquals(0, MapData.parse(json.parseToJsonElement("""{"updated":null,"markers":[]}""")).markers.size)
    }

    @Test
    fun mapMarkersSortedByTimeDescending() {
        val data = MapData.parse(
            json.parseToJsonElement(
                """{"markers":[
                ["older",1,48.0,10.0,0,100,"\/older.jpg"],
                ["newer",2,48.0,10.0,0,200,"\/newer.jpg"]
                ]}""",
            ),
        )
        assertEquals("newer", data.markers[0].assetId)
        assertEquals("older", data.markers[1].assetId)
    }

    @Test
    fun duplicatesKeepImmichsSuggestionElseTheLargest() {
        val group = json.decodeFromString<DuplicateGroup>(
            """{"id":"d1","keep":["b"],"items":[{"id":"a","fileId":1,"bytes":10},{"id":"b","fileId":2,"bytes":5}]}""",
        )
        assertEquals(setOf(2L), group.suggestedKeep())
        assertEquals(setOf(1L), group.copy(keep = emptyList()).suggestedKeep())
    }

    @Test
    fun transferAnswer() {
        val t = json.decodeFromString<Transfer>(
            """{"done":1,"failed":[{"fileId":9,"name":"x.jpg","error":"Not allowed"}],"moved":[{"fileId":3,"from":"\/Photos","fromName":"a.jpg"}]}""",
        )
        assertEquals(listOf(Moved(3, "/Photos", "a.jpg")), t.moved)
        assertEquals("x.jpg", t.failed.single().name)
    }

    @Test
    fun privateStateFromServer() {
        val p = json.decodeFromString<PrivateState>(
            """{"folders":[],"people":[],"files":3,"folderCount":1,"peopleCount":0,"unlocked":false,"locked":true,"askPassword":true}""",
        )
        assertEquals(3, p.files)
        assertEquals(false, p.unlocked)
        assertEquals(true, p.askPassword)
    }
}
