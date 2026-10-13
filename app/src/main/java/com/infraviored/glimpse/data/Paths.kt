package com.infraviored.glimpse.data

/** Folder paths in the user's files, as Glimpse writes them: "/", "/Photos", "/Photos/2024". */
object Paths {
    fun join(folder: String, name: String): String = normalize(folder).trimEnd('/') + "/" + name.trim('/')

    fun normalize(path: String): String {
        val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
        return "/" + parts.joinToString("/")
    }

    fun parent(path: String): String = normalize(path).substringBeforeLast('/', "").ifEmpty { "/" }

    fun name(path: String): String = normalize(path).substringAfterLast('/')

    /** Most recent first, without duplicates, at most [max]. */
    fun recent(list: List<String>, added: String, max: Int = 5): List<String> =
        (listOf(normalize(added)) + list.map(::normalize).filter { it != normalize(added) }).take(max)
}
