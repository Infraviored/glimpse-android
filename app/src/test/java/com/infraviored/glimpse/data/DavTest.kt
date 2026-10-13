package com.infraviored.glimpse.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DavTest {

    @Test
    fun albumNamesSkipTheCollectionAndDecode() {
        val xml = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/remote.php/dav/photos/flo/albums/</d:href></d:response>
              <d:response><d:href>/remote.php/dav/photos/flo/albums/Sommer%202024/</d:href></d:response>
              <d:response><d:href>/remote.php/dav/photos/flo/albums/apfel/</d:href></d:response>
              <d:response><d:href>/remote.php/dav/photos/flo/albums/Tom%20%26%20Jerry/</d:href></d:response>
              <d:response><d:href>/remote.php/dav/photos/flo/albums/1+1/</d:href></d:response>
            </d:multistatus>"""
        assertEquals(listOf("1+1", "apfel", "Sommer 2024", "Tom & Jerry"), Dav.albumNames(xml))
    }

    @Test
    fun otherNamespacePrefix() {
        val xml = """<a:multistatus xmlns:a="DAV:"><a:response><a:href>/dav/albums/</a:href></a:response>
            <a:response><a:href>/dav/albums/X</a:href></a:response></a:multistatus>"""
        assertEquals(listOf("X"), Dav.albumNames(xml))
    }

    @Test
    fun noAlbums() {
        assertEquals(emptyList<String>(), Dav.albumNames("""<d:multistatus xmlns:d="DAV:"><d:response><d:href>/a/</d:href></d:response></d:multistatus>"""))
    }
}
