package com.infraviored.glimpse.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.UnknownHostException

class ErrorsTest {
    @Test
    fun otherErrorsKeepTheirMessage() {
        assertEquals("Not found", errorText(ApiException(404, "Not found")))
        assertEquals("IllegalStateException", errorText(IllegalStateException()))
    }

    @Test
    fun withoutResourcesNetworkErrorsKeepTheirMessage() {
        Errors.context = null
        assertEquals("host", errorText(UnknownHostException("host")))
    }
}
