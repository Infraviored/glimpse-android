package com.infraviored.glimpse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.R
import com.infraviored.glimpse.ui.components.GlimpseSheet
import com.infraviored.glimpse.ui.components.SheetTitle
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sensitive pictures (the server's SensitiveContent): optional, the admin allows it and the user
 * turns it on on the web page. The app shows the mark, offers marking by hand and the choice
 * between all, without and only sensitive pictures.
 */
suspend fun AppState.loadSensitive() {
    val status = runCatching { api.sensitiveStatus() }.getOrNull() ?: return
    sensitive = if (status.enabled) runCatching { api.sensitivePrefs() }.getOrNull() else null
}

/** The three modes, as stored on the server, with their labels. */
@Composable
fun sensitiveModes(): List<Pair<String, String>> = listOf(
    "all" to stringResource(R.string.sensitive_all),
    "hide" to stringResource(R.string.sensitive_hide),
    "only" to stringResource(R.string.sensitive_only),
)

/** Short name of a filter that is on ("Without sensitive"), null for all pictures. */
@Composable
fun sensitiveFilterName(mode: String?): String? = when (mode) {
    "hide" -> stringResource(R.string.sensitive_hide_short)
    "only" -> stringResource(R.string.sensitive_only_short)
    else -> null
}

/** All, without, or only sensitive pictures; applies everywhere at once. */
@Composable
fun SensitiveModeSheet(
    onDismiss: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope(),
) {
    val app = LocalApp.current
    val current = app.sensitive?.mode ?: "all"
    val failed = stringResource(R.string.action_failed, "%s")
    var inFlight by remember { mutableStateOf(false) }
    GlimpseSheet(onDismiss) {
        SheetTitle(stringResource(R.string.sensitive_title))
        sensitiveModes().forEach { (mode, label) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = !inFlight) {
                    if (inFlight) return@clickable
                    if (mode == current) {
                        onDismiss()
                        return@clickable
                    }
                    inFlight = true
                    scope.launch {
                        try {
                            app.sensitiveModeChanged(app.api.setSensitiveMode(mode))
                        } catch (e: Exception) {
                            app.notices.show(failed.replace("%s", e.message ?: e.javaClass.simpleName))
                        } finally {
                            inFlight = false
                            onDismiss()
                        }
                    }
                }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = mode == current,
                    onClick = null,
                    enabled = !inFlight,
                    colors = RadioButtonDefaults.colors(selectedColor = glimpse.accent),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (inFlight) glimpse.muted else glimpse.text,
                )
            }
        }
    }
}
