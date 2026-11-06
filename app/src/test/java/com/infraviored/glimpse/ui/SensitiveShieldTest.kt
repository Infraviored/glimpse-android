package com.infraviored.glimpse.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SensitiveShieldTest {
    @Test
    fun tapsOnlyEverShowFewerSensitivePictures() {
        assertEquals("all", nextSensitiveMode("only"))
        assertEquals("hide", nextSensitiveMode("all"))
    }
}
