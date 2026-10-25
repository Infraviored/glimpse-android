package com.infraviored.glimpse.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.ApiException
import com.infraviored.glimpse.data.errorText
import com.infraviored.glimpse.ui.components.GlimpseSheet
import com.infraviored.glimpse.ui.components.PillField
import com.infraviored.glimpse.ui.components.SheetButtons
import com.infraviored.glimpse.ui.components.SheetTitle
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Ask the server how the private area stands. */
suspend fun AppState.loadPrivate() {
    runCatching { api.privateState() }.onSuccess { privateArea = it }
}

/**
 * Lock, or unlock without a password (if the user turned that off). With a password the
 * caller shows [UnlockSheet] instead.
 */
fun AppState.togglePrivate(scope: CoroutineScope, failed: (String) -> Unit) {
    val state = privateArea ?: return
    scope.launch {
        try {
            if (state.unlocked) api.lockPrivate() else api.unlockPrivate(null)
            privacyChanged(api.privateState())
        } catch (e: Exception) {
            failed(errorText(e))
        }
    }
}

/** Nextcloud's password, as its own "confirm your password" asks for it. */
@Composable
fun UnlockSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        if (password.isEmpty() || busy) return
        busy = true
        error = null
        scope.launch {
            try {
                app.api.unlockPrivate(password)
                app.privacyChanged(app.api.privateState())
                onDismiss()
            } catch (e: ApiException) {
                error = when (e.code) {
                    403 -> context.getString(R.string.private_wrong_password)
                    429 -> context.getString(R.string.private_too_many)
                    else -> e.message
                }
            } catch (e: Exception) {
                error = errorText(e)
            } finally {
                busy = false
                password = ""
            }
        }
    }
    GlimpseSheet(onDismiss) {
        SheetTitle(stringResource(R.string.private_unlock_title), stringResource(R.string.private_unlock_text))
        PillField(password, { password = it }, stringResource(R.string.private_password), password = true, onDone = ::submit)
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        SheetButtons(
            cancel = stringResource(R.string.cancel),
            confirm = stringResource(R.string.private_unlock),
            onCancel = onDismiss,
            onConfirm = ::submit,
            enabled = password.isNotEmpty(),
            busy = busy,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.private_unlock_hint), style = MaterialTheme.typography.bodySmall, color = glimpse.muted)
    }
}
