package com.infraviored.glimpse.ui.timeline

import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.ui.byMonth
import com.infraviored.glimpse.ui.components.gridKeys

/** Items in front of the photos in a timeline grid: the one header item. */
internal const val HEADER_ITEMS = 1

/**
 * The year a pick on the scrubber jumps to, or null for "the top": a year at or after the
 * newest one there is (and never later than [now]) is simply the start of the list.
 */
internal fun jumpTarget(year: Int?, years: List<Int>, now: Int): Int? {
    val top = minOf(now, years.maxOrNull() ?: now)
    return year?.takeIf { it < top }
}

/** The list after a jump to [year] starts at the end of that year. */
internal fun yearEnd(year: Int): String = "${year + 1}-01-01T00:00:00.000Z"

/**
 * What the list shows: after a jump, the newer pictures loaded so far (oldest first from the
 * server, so turned around) in front of the pictures from the jump point back.
 */
internal fun timelineItems(jumped: Boolean, newer: List<Item>, older: List<Item>, deleted: Set<Long>): List<Item> {
    val all = if (jumped) newer.asReversed() + older else older
    return all.filterNot { it.fileId in deleted }
}

/**
 * Paging source for the viewer: after a jump the timeline combines newer and older
 * sets that a single [Paged] cannot represent, so the viewer receives the snapshot items.
 */
internal fun <T> viewerSource(jumped: Boolean, source: T?): T? = if (jumped) null else source


/**
 * The picture to hold in place when no photo is on screen: the topmost newer one shown so far
 * (the last the server sent), else the first one from the jump point.
 */
internal fun fallbackAnchor(shownNewer: List<Item>, older: List<Item>, deleted: Set<Long>): Long? =
    (shownNewer.lastOrNull { it.fileId !in deleted } ?: older.firstOrNull { it.fileId !in deleted })?.fileId

/**
 * Where the picture [anchorKey] sits in the grid once [newer] has joined the list, counting
 * the header item; -1 if it is not in there.
 */
internal fun anchorIndex(newer: List<Item>, older: List<Item>, deleted: Set<Long>, anchorKey: Long, unknown: String): Int {
    val sections = byMonth(timelineItems(true, newer, older, deleted), unknown)
    val index = gridKeys(sections).indexOf(anchorKey)
    return if (index < 0) -1 else index + HEADER_ITEMS
}
