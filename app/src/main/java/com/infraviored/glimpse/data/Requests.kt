package com.infraviored.glimpse.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Where the faces go in "Not this person". */
sealed interface FaceTarget {
    /** An existing person. */
    data class Person(val id: String) : FaceTarget

    /** A new person with this name. */
    data class NewName(val name: String) : FaceTarget

    /** One new person without a name, for all of them. */
    data object Unnamed : FaceTarget

    /** No face at all: removed from the recognition. */
    data object NotAFace : FaceTarget
}

/** The JSON bodies Glimpse's API takes, as its web page sends them. */
object Requests {
    fun updatePerson(name: String?, hidden: Boolean?, favorite: Boolean?): JsonObject = buildJsonObject {
        name?.let { put("name", it.trim()) }
        hidden?.let { put("hidden", it) }
        favorite?.let { put("favorite", it) }
    }

    fun merge(others: List<String>): JsonObject = buildJsonObject {
        putJsonArray("ids") { others.distinct().forEach { add(JsonPrimitive(it)) } }
    }

    fun moveFaces(assetIds: List<String>, target: FaceTarget): JsonObject = buildJsonObject {
        putJsonArray("assetIds") { assetIds.distinct().forEach { add(JsonPrimitive(it)) } }
        put("personId", (target as? FaceTarget.Person)?.id.orEmpty())
        put("newName", (target as? FaceTarget.NewName)?.name?.trim().orEmpty())
        put("unnamed", target == FaceTarget.Unnamed)
        put("notAFace", target == FaceTarget.NotAFace)
    }

    fun transfer(fileIds: List<Long>, target: String, copy: Boolean): JsonObject = buildJsonObject {
        putJsonArray("fileIds") { fileIds.distinct().forEach { add(JsonPrimitive(it)) } }
        put("target", target)
        put("mode", if (copy) "copy" else "move")
    }

    fun undo(moves: List<Moved>): JsonObject = buildJsonObject {
        putJsonArray("moves") {
            moves.forEach { m ->
                addJsonObject {
                    put("fileId", m.fileId)
                    put("from", m.from)
                    m.fromName?.let { put("fromName", it) }
                }
            }
        }
    }

    fun tag(fileIds: List<Long>, tagIds: List<Long>, newTags: List<String>): JsonObject = buildJsonObject {
        putJsonArray("fileIds") { fileIds.distinct().forEach { add(JsonPrimitive(it)) } }
        putJsonArray("tagIds") { tagIds.distinct().forEach { add(JsonPrimitive(it)) } }
        putJsonArray("newTags") { newTags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { add(JsonPrimitive(it)) } }
    }

    /** fileId => true (sensitive), false (not), null (back to what was recognised) */
    fun sensitiveMarks(marks: Map<String, Boolean?>): JsonObject = buildJsonObject {
        put("marks", buildJsonObject { marks.forEach { (id, on) -> put(id, on) } })
    }

    fun prefs(prefs: Map<String, String>): JsonObject = buildJsonObject {
        put("prefs", buildJsonObject { prefs.forEach { (k, v) -> put(k, v) } })
    }

    fun privateFiles(fileIds: List<Long>, on: Boolean): JsonObject = buildJsonObject {
        putJsonArray("fileIds") { fileIds.distinct().forEach { add(JsonPrimitive(it)) } }
        put("on", on)
    }
}
