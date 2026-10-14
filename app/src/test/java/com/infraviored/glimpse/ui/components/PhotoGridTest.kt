package com.infraviored.glimpse.ui.components

import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.ui.Section
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoGridTest {
    private fun item(id: Long) = Item(fileId = id, date = "2024-05-01T12:00:00.000Z")

    @Test
    fun itemIndexInEmptySectionsReturnsNegativeOne() {
        assertEquals(-1, photoGridItemIndex(emptyList(), 1L, headerCount = 0))
        assertEquals(-1, photoGridItemIndex(emptyList(), 1L, headerCount = 1))
    }

    @Test
    fun itemIndexWithoutYearBandCountsHeaderAndHead() {
        val sections = listOf(Section(key = "2024-05", title = "May 2024", items = listOf(item(101L), item(102L))))
        assertEquals(1, photoGridItemIndex(sections, 101L, headerCount = 0))
        assertEquals(2, photoGridItemIndex(sections, 102L, headerCount = 0))
        assertEquals(2, photoGridItemIndex(sections, 101L, headerCount = 1))
        assertEquals(3, photoGridItemIndex(sections, 102L, headerCount = 1))
    }

    @Test
    fun itemIndexWithYearBandCountsYearBandAndHead() {
        val sections = listOf(
            Section(key = "2023-12", title = "December 2023", items = listOf(item(201L), item(202L)), year = 2023),
        )
        assertEquals(2, photoGridItemIndex(sections, 201L, headerCount = 0))
        assertEquals(3, photoGridItemIndex(sections, 202L, headerCount = 0))
        assertEquals(3, photoGridItemIndex(sections, 201L, headerCount = 1))
        assertEquals(4, photoGridItemIndex(sections, 202L, headerCount = 1))
    }

    @Test
    fun itemIndexAcrossMultipleSections() {
        val sections = listOf(
            Section(key = "2024-05", title = "May 2024", items = listOf(item(101L), item(102L))),
            Section(key = "2023-12", title = "December 2023", items = listOf(item(201L), item(202L)), year = 2023),
        )
        assertEquals(2, photoGridItemIndex(sections, 101L, headerCount = 1))
        assertEquals(3, photoGridItemIndex(sections, 102L, headerCount = 1))
        assertEquals(6, photoGridItemIndex(sections, 201L, headerCount = 1))
        assertEquals(7, photoGridItemIndex(sections, 202L, headerCount = 1))
    }

    @Test
    fun missingItemReturnsNegativeOne() {
        val sections = listOf(Section(key = "all", title = "", items = listOf(item(1L))))
        assertEquals(-1, photoGridItemIndex(sections, 999L, headerCount = 0))
        assertEquals(-1, photoGridItemIndex(sections, 999L, headerCount = 1))
    }
}
