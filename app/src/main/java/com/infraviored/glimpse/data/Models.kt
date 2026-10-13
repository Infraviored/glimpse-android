package com.infraviored.glimpse.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** One picture or video as Glimpse lists it. [id] is the Immich asset, null for files Immich has not indexed. */
@Serializable
data class Item(
    val id: String? = null,
    val fileId: Long,
    val type: String = "IMAGE",
    val date: String? = null,
    val name: String = "",
    val path: String = "",
    val mime: String = "",
    /** A video found by one of its stills: the second the search found it at */
    val at: Double? = null,
    /** That still, shown instead of the video's own thumbnail */
    val thumbId: String? = null,
) {
    val isVideo: Boolean get() = type == "VIDEO" || mime.startsWith("video/")

    /**
     * When it was taken, as the clock on the camera showed it. Immich keeps that local time
     * as if it were UTC, so it is read back in UTC.
     */
    val taken: LocalDateTime?
        get() = date?.let {
            try {
                OffsetDateTime.parse(it).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
            } catch (e: Exception) {
                null
            }
        }
}

@Serializable
data class Page(
    val items: List<Item> = emptyList(),
    val nextPage: Int? = null,
    val years: List<Int>? = null,
    val person: Person? = null,
)

@Serializable
data class Person(
    val id: String,
    val name: String = "",
    val hidden: Boolean = false,
    val favorite: Boolean = false,
    @SerialName("private") val isPrivate: Boolean = false,
    val count: Int = 0,
)

@Serializable
data class People(
    val people: List<Person> = emptyList(),
    val canManage: Boolean = false,
)

@Serializable
data class Memory(val year: Int, val items: List<Item> = emptyList())

@Serializable
data class FolderRef(val name: String, val path: String)

@Serializable
data class Folder(
    val path: String = "/",
    val folders: List<FolderRef> = emptyList(),
    val items: List<Item> = emptyList(),
    val truncated: Boolean = false,
    @SerialName("private") val isPrivate: Boolean = false,
)

/** A face Immich found, as a box relative to the picture (0..1). */
@Serializable
data class Face(
    val id: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
    val personId: String? = null,
    val personName: String? = null,
    val known: Boolean = false,
)

@Serializable
data class Faces(val faces: List<Face> = emptyList(), val canManage: Boolean = false)

@Serializable
data class Tag(val id: Long, val name: String)

@Serializable
data class PersonUpdate(val id: String, val name: String = "", val hidden: Boolean = false, val favorite: Boolean = false)

@Serializable
data class MovedFaces(
    val moved: List<String> = emptyList(),
    val faces: Int = 0,
    val failed: List<String> = emptyList(),
    val personId: String? = null,
)

/** A file that was moved, and where from: enough to move it back. */
@Serializable
data class Moved(val fileId: Long, val from: String, val fromName: String? = null)

@Serializable
data class Failed(val fileId: Long = 0, val name: String = "", val error: String = "")

@Serializable
data class Transfer(val done: Int = 0, val failed: List<Failed> = emptyList(), val moved: List<Moved> = emptyList())

@Serializable
data class Done(val done: Int = 0, val failed: List<Failed> = emptyList())

@Serializable
data class PrivateState(
    val files: Int = 0,
    val folderCount: Int = 0,
    val peopleCount: Int = 0,
    val unlocked: Boolean = false,
    val askPassword: Boolean = true,
)

/** A picture in a duplicate group, with what is needed to choose which to keep. */
@Serializable
data class DuplicateItem(
    val id: String? = null,
    val fileId: Long,
    val type: String = "IMAGE",
    val date: String? = null,
    val name: String = "",
    val path: String = "",
    val mime: String = "",
    val bytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
) {
    fun toItem() = Item(id, fileId, type, date, name, path, mime)
}

@Serializable
data class DuplicateGroup(
    val id: String,
    /** Immich's suggestion of what to keep (asset ids). */
    val keep: List<String> = emptyList(),
    val items: List<DuplicateItem> = emptyList(),
) {
    /** What is kept unless the user says otherwise: Immich's choice, else the largest file. */
    fun suggestedKeep(): Set<Long> {
        val suggested = items.filter { it.id != null && it.id in keep }.map { it.fileId }.toSet()
        if (suggested.isNotEmpty()) return suggested
        return items.maxByOrNull { it.bytes }?.let { setOf(it.fileId) } ?: emptySet()
    }
}
