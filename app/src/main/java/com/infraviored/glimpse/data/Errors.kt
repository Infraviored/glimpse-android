package com.infraviored.glimpse.data

import android.content.Context
import com.infraviored.glimpse.R
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Words for errors, from the app's resources; set once the app starts. */
object Errors {
    @Volatile
    var context: Context? = null
}

/**
 * What to tell the user about [e]: network trouble in plain words instead of the exception's
 * own text ("Unable to resolve host …"), the server's own message for its errors.
 */
fun errorText(e: Throwable): String {
    val res = Errors.context?.resources
    val id = when (e) {
        is UnknownHostException -> R.string.error_no_connection
        is ConnectException, is NoRouteToHostException -> R.string.error_unreachable
        is SocketTimeoutException -> R.string.error_timeout
        is SSLException -> R.string.error_secure
        else -> null
    }
    return if (id != null && res != null) res.getString(id) else errorText(e)
}
