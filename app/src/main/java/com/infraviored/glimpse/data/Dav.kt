package com.infraviored.glimpse.data

import java.net.URLDecoder

/** The little WebDAV the albums need (Nextcloud Photos keeps them under dav/photos). */
object Dav {
    const val PROPFIND_NAME =
        "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:displayname/></d:prop></d:propfind>"

    private val href = Regex("<(?:[A-Za-z0-9_-]+:)?href>([^<]*)</(?:[A-Za-z0-9_-]+:)?href>")

    /**
     * Album names from a Depth 1 PROPFIND of the albums collection: the last segment of every
     * href but the first (the collection itself), sorted.
     */
    fun albumNames(xml: String): List<String> =
        href.findAll(xml).map { it.groupValues[1].trim() }.toList()
            .drop(1)
            .map { decode(unescape(it).trimEnd('/').substringAfterLast('/')) }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    private fun decode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
}
