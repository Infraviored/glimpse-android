package com.infraviored.glimpse.ui

import com.infraviored.glimpse.data.Item
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VideoMomentTest {
    @Test
    fun clockShowsMinutesAndHours() {
        assertEquals("0:00", clock(-1))
        assertEquals("0:07", clock(7))
        assertEquals("1:23", clock(83))
        assertEquals("1:02:03", clock(3723))
    }

    @Test
    fun searchHitCarriesMomentAndStill() {
        val json = Json { ignoreUnknownKeys = true }
        val hit = json.decodeFromString<Item>("""{"id":"v","fileId":5,"type":"VIDEO","at":37.5,"thumbId":"s"}""")
        assertNotNull(hit.at)
        assertEquals(37.5, hit.at ?: 0.0, 0.0)
        assertEquals("s", hit.thumbId)
        val plain = json.decodeFromString<Item>("""{"id":"v","fileId":5,"type":"VIDEO"}""")
        assertNull(plain.at)
        assertNull(plain.thumbId)
    }
}
