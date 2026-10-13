package com.infraviored.glimpse.ui.timeline

import com.infraviored.glimpse.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineLogicTest {
    private fun item(id: Long, date: String) = Item(fileId = id, date = "${date}T12:00:00.000Z")

    // Newest first from the jump point back
    private val older = listOf(item(1, "2019-12-20"), item(2, "2019-11-02"))
    // Oldest first from the server, forward to today
    private val newer = listOf(item(3, "2020-01-05"), item(4, "2020-03-09"))

    @Test
    fun jumpToNewestYearIsTheTop() {
        assertNull(jumpTarget(null, listOf(2026, 2020), 2026))
        assertNull(jumpTarget(2026, listOf(2026, 2020), 2026))
        assertEquals(2020, jumpTarget(2020, listOf(2026, 2020), 2026))
    }

    @Test
    fun personWhoseNewestYearIsPastStartsThere() {
        // Someone last seen in 2023: picking 2023 is just the start of their list
        assertNull(jumpTarget(2023, listOf(2023, 2015), 2026))
        assertEquals(2015, jumpTarget(2015, listOf(2023, 2015), 2026))
    }

    @Test
    fun noYearsYetFallsBackToNow() {
        assertNull(jumpTarget(2026, emptyList(), 2026))
        assertEquals(2010, jumpTarget(2010, emptyList(), 2026))
    }

    @Test
    fun futureDatedPicturesDoNotMoveTheTop() {
        assertNull(jumpTarget(2026, listOf(2030, 2026, 2001), 2026))
        assertEquals(2001, jumpTarget(2001, listOf(2030, 2026, 2001), 2026))
    }

    @Test
    fun jumpStartsAtTheEndOfTheYear() {
        assertEquals("2016-01-01T00:00:00.000Z", yearEnd(2015))
    }

    @Test
    fun itemsPutNewerInFrontNewestFirst() {
        assertEquals(listOf(4L, 3L, 1L, 2L), timelineItems(true, newer, older, emptySet()).map { it.fileId })
        assertEquals(listOf(1L, 2L), timelineItems(false, newer, older, emptySet()).map { it.fileId })
        assertEquals(listOf(4L, 1L, 2L), timelineItems(true, newer, older, setOf(3L)).map { it.fileId })
    }

    @Test
    fun viewerSourceOmittedWhenJumped() {
        assertNull(viewerSource(true, "dummy-paged"))
        assertEquals("dummy-paged", viewerSource(false, "dummy-paged"))
    }


    @Test
    fun fallbackAnchorIsTheTopmostPictureShown() {
        // The newest of the newer pictures shown so far (last from the server) sits on top
        assertEquals(4L, fallbackAnchor(newer, older, emptySet()))
        assertEquals(3L, fallbackAnchor(newer, older, setOf(4L)))
        assertEquals(1L, fallbackAnchor(emptyList(), older, emptySet()))
        assertEquals(2L, fallbackAnchor(emptyList(), older, setOf(1L)))
        assertNull(fallbackAnchor(emptyList(), emptyList(), emptySet()))
    }

    @Test
    fun anchorIndexCountsBandsHeadsAndTheHeader() {
        // header, 2020 band, March, 4, January, 3, 2019 band, December, 1, November, 2
        assertEquals(8, anchorIndex(newer, older, emptySet(), 1L, "?"))
        assertEquals(3, anchorIndex(newer, older, emptySet(), 4L, "?"))
        assertEquals(-1, anchorIndex(newer, older, emptySet(), 99L, "?"))
        // A deleted picture is not in the grid, so the ones below move up
        assertEquals(6, anchorIndex(newer, older, setOf(3L), 1L, "?"))
        // String keys for year bands and month heads can also anchor
        assertEquals(1, anchorIndex(newer, older, emptySet(), "year-2020-03", "?"))
        assertEquals(2, anchorIndex(newer, older, emptySet(), "head-2020-03", "?"))
        assertEquals(4, anchorIndex(newer, older, emptySet(), "head-2020-01", "?"))
        assertEquals(6, anchorIndex(newer, older, emptySet(), "year-2019-12", "?"))
        assertEquals(7, anchorIndex(newer, older, emptySet(), "head-2019-12", "?"))
    }

    @Test
    fun contentFittingScreenArmsWithoutScrolling() {
        // Fits entirely on screen: neither backward nor forward scroll possible
        assertTrue(shouldArmNewer(isScrollInProgress = false, scrolledBackward = false, canScrollBackward = false, canScrollForward = false))
        assertTrue(shouldArmNewer(isScrollInProgress = true, scrolledBackward = false, canScrollBackward = false, canScrollForward = false))
    }

    @Test
    fun draggingAtTopBoundaryArms() {
        // At the top of an overflowing list: cannot scroll backward, user dragging downwards
        assertTrue(shouldArmNewer(isScrollInProgress = true, scrolledBackward = false, canScrollBackward = false, canScrollForward = true))
    }

    @Test
    fun idleAtTopBoundaryDoesNotArm() {
        // Overflowing list at top boundary without user interaction
        assertFalse(shouldArmNewer(isScrollInProgress = false, scrolledBackward = false, canScrollBackward = false, canScrollForward = true))
    }

    @Test
    fun scrollingBackwardArmsWhenContentScrollable() {
        // Middle of list, user scrolling up / backward
        assertTrue(shouldArmNewer(isScrollInProgress = true, scrolledBackward = true, canScrollBackward = true, canScrollForward = true))
    }

    @Test
    fun scrollingForwardDoesNotArmWhenContentScrollable() {
        // Middle of list, user scrolling down / forward
        assertFalse(shouldArmNewer(isScrollInProgress = true, scrolledBackward = false, canScrollBackward = true, canScrollForward = true))
    }

    @Test
    fun idleDoesNotArmWhenContentScrollable() {
        // Middle of list, stationary
        assertFalse(shouldArmNewer(isScrollInProgress = false, scrolledBackward = false, canScrollBackward = true, canScrollForward = true))
        assertFalse(shouldArmNewer(isScrollInProgress = false, scrolledBackward = true, canScrollBackward = true, canScrollForward = true))
    }

    @Test
    fun bottomBoundaryScrollBehavior() {
        // At bottom of list: backward scroll arms, forward does not
        assertTrue(shouldArmNewer(isScrollInProgress = true, scrolledBackward = true, canScrollBackward = true, canScrollForward = false))
        assertFalse(shouldArmNewer(isScrollInProgress = true, scrolledBackward = false, canScrollBackward = true, canScrollForward = false))
    }
}
