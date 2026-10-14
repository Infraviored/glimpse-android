package com.infraviored.glimpse.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestsTest {

    @Test
    fun updatePersonSendsOnlyWhatChanges() {
        assertEquals("""{"name":"Tanja"}""", Requests.updatePerson(" Tanja ", null, null).toString())
        assertEquals("""{"hidden":true}""", Requests.updatePerson(null, true, null).toString())
        assertEquals("""{"favorite":false}""", Requests.updatePerson(null, null, false).toString())
    }

    @Test
    fun mergeSendsTheOthersOnce() {
        assertEquals("""{"ids":["b","c"]}""", Requests.merge(listOf("b", "c", "b")).toString())
    }

    @Test
    fun moveFacesNamesExactlyOneTarget() {
        assertEquals(
            """{"assetIds":["a1","a2"],"personId":"p2","newName":"","unnamed":false,"notAFace":false}""",
            Requests.moveFaces(listOf("a1", "a2", "a1"), FaceTarget.Person("p2")).toString(),
        )
        assertEquals(
            """{"assetIds":["a1"],"personId":"","newName":"Oma","unnamed":false,"notAFace":false}""",
            Requests.moveFaces(listOf("a1"), FaceTarget.NewName("  Oma ")).toString(),
        )
        assertEquals(
            """{"assetIds":["a1"],"personId":"","newName":"","unnamed":true,"notAFace":false}""",
            Requests.moveFaces(listOf("a1"), FaceTarget.Unnamed).toString(),
        )
        assertEquals(
            """{"assetIds":["a1"],"personId":"","newName":"","unnamed":false,"notAFace":true}""",
            Requests.moveFaces(listOf("a1"), FaceTarget.NotAFace).toString(),
        )
    }

    @Test
    fun transferAndUndo() {
        assertEquals("""{"fileIds":[1,2],"target":"/Photos/2024","mode":"copy"}""", Requests.transfer(listOf(1, 2, 2), "/Photos/2024", true).toString())
        assertEquals("""{"fileIds":[3],"target":"/","mode":"move"}""", Requests.transfer(listOf(3), "/", false).toString())
        assertEquals(
            """{"moves":[{"fileId":3,"from":"/Photos","fromName":"a.jpg"},{"fileId":4,"from":"/x"}]}""",
            Requests.undo(listOf(Moved(3, "/Photos", "a.jpg"), Moved(4, "/x"))).toString(),
        )
    }

    @Test
    fun tagDropsEmptyNewNames() {
        assertEquals(
            """{"fileIds":[1],"tagIds":[7],"newTags":["Urlaub"]}""",
            Requests.tag(listOf(1), listOf(7, 7), listOf(" Urlaub ", "", "Urlaub")).toString(),
        )
    }

    @Test
    fun sensitiveMarks() {
        assertEquals(
            """{"marks":{"5":true,"6":null}}""",
            Requests.sensitiveMarks(linkedMapOf("5" to true, "6" to null)).toString(),
        )
        assertEquals("""{"prefs":{"sensitive_mode":"hide"}}""", Requests.prefs(mapOf("sensitive_mode" to "hide")).toString())
    }

    @Test
    fun privateFiles() {
        assertEquals("""{"fileIds":[5,6],"on":false}""", Requests.privateFiles(listOf(5, 6), false).toString())
    }
}

class PathsTest {
    @Test
    fun joinAndParent() {
        assertEquals("/a.jpg", Paths.join("/", "a.jpg"))
        assertEquals("/Photos/2024/a.jpg", Paths.join("/Photos/2024/", "a.jpg"))
        assertEquals("/Photos", Paths.parent("/Photos/2024"))
        assertEquals("/", Paths.parent("/Photos"))
        assertEquals("/", Paths.parent("/"))
        assertEquals("2024", Paths.name("/Photos/2024/"))
    }

    @Test
    fun recentFoldersNewestFirst() {
        assertEquals(listOf("/b", "/a"), Paths.recent(listOf("/a", "/b/"), "/b"))
        assertEquals(listOf("/n", "/1", "/2"), Paths.recent(listOf("/1", "/2", "/3"), "/n", max = 3))
    }
}
