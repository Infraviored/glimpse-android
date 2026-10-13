package com.infraviored.glimpse.ui

import com.infraviored.glimpse.ui.duplicates.size
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DuplicatesTest {
    @Test
    fun sizes() {
        Locale.setDefault(Locale.US)
        assertEquals("999 B", size(999))
        assertEquals("561 kB", size(561_000))
        assertEquals("1.2 MB", size(1_248_288))
        assertEquals("3.4 GB", size(3_400_000_000))
    }
}
