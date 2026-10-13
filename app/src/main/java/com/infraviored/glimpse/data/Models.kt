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
