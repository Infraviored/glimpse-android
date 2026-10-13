package com.infraviored.glimpse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizeServerTest {

    private fun n(input: String) = Nextcloud.normalizeServer(input)

    @Test
    fun addsHttpsAndDropsTrailingSlash() {
        assertEquals("https://cloud.example.com", n("cloud.example.com"))
        assertEquals("https://cloud.example.com", n("  cloud.example.com/  "))
        assertEquals("https://cloud.example.com", n("https://Cloud.Example.com/"))
    }

    @Test
    fun cutsPastedPageAddresses() {
        assertEquals("https://cloud.example.com", n("https://cloud.example.com/index.php/apps/glimpse/"))
        assertEquals("https://cloud.example.com", n("https://cloud.example.com/apps/files/?dir=/"))
        assertEquals("https://cloud.example.com", n("https://cloud.example.com/login?redirect_url=x"))
    }

    @Test
    fun keepsSubFolderPortAndHttp() {
        assertEquals("https://example.com/nextcloud", n("example.com/nextcloud/index.php/apps/glimpse"))
        assertEquals("http://192.168.1.5:8080", n("http://192.168.1.5:8080/"))
    }

    @Test
    fun rejectsNonsense() {
        assertNull(n(""))
        assertNull(n("cloud example com"))
        assertNull(n("ftp://cloud.example.com"))
        assertNull(n("javascript:alert(1)"))
    }
}
