package com.infraviored.glimpse.ui

import com.infraviored.glimpse.data.Item
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** A run of pictures under one heading. [year] is set on the first section of each year. */
data class Section(
    val key: String,
    val title: String,
    val items: List<Item>,
    val year: Int? = null,
)

/**
 * Consecutive pictures of one month form a section, in the order they come. Each year other
 * than this one gets a band with its number, the timeline's way of saying "further back".
 */
fun byMonth(items: List<Item>, unknown: String, locale: Locale = Locale.getDefault(), thisYear: Int = YearMonth.now().year): List<Section> {
    val out = mutableListOf<Section>()
    var prevYear: Int = thisYear
    // Keyed by month, not position: pictures put in front of the list keep the keys below
    val seen = mutableMapOf<String, Int>()
    var i = 0
    while (i < items.size) {
        val ym = items[i].taken?.let(YearMonth::from)
        var j = i + 1
        while (j < items.size && items[j].taken?.let(YearMonth::from) == ym) j++
        val title = ym?.month?.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            ?.replaceFirstChar { it.titlecase(locale) } ?: unknown
        val band = ym?.year?.takeIf { it != prevYear }
        val n = seen.merge("$ym", 1, Int::plus)!!
        out += Section(if (n == 1) "$ym" else "$ym#$n", title, items.subList(i, j).toList(), band)
        if (ym != null) prevYear = ym.year
        i = j
    }
    return out
}

fun longDate(dt: LocalDateTime, locale: Locale = Locale.getDefault()): String =
    dt.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))

fun shortTime(dt: LocalDateTime, locale: Locale = Locale.getDefault()): String =
    dt.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

/** "5.569" / "5,569": counts with the locale's grouping. */
/** "1:23" or "1:02:03": a place in a video. */
fun clock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun count(n: Int): String = java.text.NumberFormat.getIntegerInstance().format(n)
