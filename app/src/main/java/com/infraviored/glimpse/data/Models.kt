package com.infraviored.glimpse.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
    /** Counted as sensitive (only sent when the user turned sensitive pictures on) */
    val sensitive: Boolean = false,
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
data class SensitiveStatus(
    /** The admin allows it */
    val available: Boolean = false,
    /** ... and the user turned it on */
    val enabled: Boolean = false,
    /** Threshold in percent => how many pictures would count */
    val counts: Map<String, Int> = emptyMap(),
    val scan: SensitiveScan? = null,
)

/** How far the detector is through the pictures. */
@Serializable
data class SensitiveScan(
    val scanned: Int = 0,
    val total: Int? = null,
    val firstPassDone: Boolean = false,
)

/** The user's settings for sensitive pictures, from their server-side preferences. */
@Serializable
data class SensitivePrefs(
    @SerialName("sensitive_enabled") val enabled: Boolean = false,
    @SerialName("sensitive_mode") val mode: String = "all",
    @SerialName("sensitive_badge") val badge: Boolean = true,
    /** How sure the detector must be, in percent */
    @SerialName("sensitive_threshold") val threshold: Int = 50,
    @SerialName("sensitive_breast") val breast: Boolean = true,
    @SerialName("sensitive_genitals") val genitals: Boolean = true,
    @SerialName("sensitive_buttocks") val buttocks: Boolean = true,
    @SerialName("sensitive_chest") val chest: Boolean = false,
)

/** After marking: how each file stands now, and the marks before (for undo). */
@Serializable(with = SensitiveMarksSerializer::class)
data class SensitiveMarks(
    val previous: Map<String, Boolean?> = emptyMap(),
    val sensitive: Map<String, Boolean> = emptyMap(),
)

object SensitiveMarksSerializer : KSerializer<SensitiveMarks> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.infraviored.glimpse.data.SensitiveMarks")

    override fun serialize(encoder: Encoder, value: SensitiveMarks) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("Only JSON supported")
        val obj = buildJsonObject {
            put("previous", buildJsonObject {
                value.previous.forEach { (k, v) -> put(k, v) }
            })
            put("sensitive", buildJsonObject {
                value.sensitive.forEach { (k, v) -> put(k, v) }
            })
        }
        json.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): SensitiveMarks {
        val json = decoder as? JsonDecoder ?: throw SerializationException("Only JSON supported")
        val root = json.decodeJsonElement() as? JsonObject ?: return SensitiveMarks()
        val previous = (root["previous"] as? JsonObject)?.mapValues { (_, v) ->
            if (v is JsonNull) null else v.jsonPrimitive.booleanOrNull
        }.orEmpty()
        val sensitive = (root["sensitive"] as? JsonObject)?.mapValues { (_, v) ->
            v.jsonPrimitive.boolean
        }.orEmpty()
        return SensitiveMarks(previous = previous, sensitive = sensitive)
    }
}

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
