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
    var i = 0
    while (i < items.size) {
        val ym = items[i].taken?.let(YearMonth::from)
        var j = i + 1
        while (j < items.size && items[j].taken?.let(YearMonth::from) == ym) j++
        val title = ym?.month?.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            ?.replaceFirstChar { it.titlecase(locale) } ?: unknown
        val band = ym?.year?.takeIf { it != prevYear }
        out += Section("$ym-$i", title, items.subList(i, j).toList(), band)
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
fun count(n: Int): String = java.text.NumberFormat.getIntegerInstance().format(n)
