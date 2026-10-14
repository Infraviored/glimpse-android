package com.infraviored.glimpse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.SensitivePrefs
import com.infraviored.glimpse.data.SensitiveStatus
import com.infraviored.glimpse.ui.components.GlimpseSheet
import com.infraviored.glimpse.ui.components.SheetTitle
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sensitive pictures (the server's SensitiveContent): optional, the admin allows it and the user
 * turns it on. What counts are the user's own terms, each down to a picture they picked, and
 * their marks by hand. The app shows the mark, offers marking and "Sensitive up to here", the
 * choice between all, without and only sensitive pictures, and the same settings as the web page.
 */
suspend fun AppState.loadSensitive() {
    val status = runCatching { api.sensitiveStatus() }.getOrNull() ?: return
    sensitiveAvailable = status.available
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

/**
 * Everything about sensitive pictures in one sheet: on or off, what is shown, the user's terms
 * (each with how many pictures it brings in; set in the search with "Sensitive up to here"),
 * and the mark on tiles. Changes are saved at once and the lists load again.
 *
 * [scope] outlives the sheet: closing it must not cancel a change on its way to the server.
 */
@Composable
fun SensitiveSheet(onDismiss: () -> Unit, scope: CoroutineScope = rememberCoroutineScope()) {
    val app = LocalApp.current
    val res = LocalContext.current.resources
    val failed = stringResource(R.string.action_failed, "%s")
    var status by remember { mutableStateOf<SensitiveStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    val prefs = app.sensitive
    LaunchedEffect(prefs) { status = runCatching { app.api.sensitiveStatus() }.getOrNull() }

    fun save(changes: Map<String, Any>) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                app.sensitiveModeChanged(app.api.saveSensitive(changes))
            } catch (e: Exception) {
                app.notices.show(failed.replace("%s", e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }
    }

    GlimpseSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle(stringResource(R.string.sensitive_title))
            SwitchRow(stringResource(R.string.sensitive_enable), prefs != null, enabled = !busy) { save(mapOf("sensitive_enabled" to it)) }
            if (prefs == null) {
                Text(stringResource(R.string.sensitive_explain), style = MaterialTheme.typography.bodySmall, color = glimpse.muted)
                return@Column
            }
            Spacer(Modifier.height(12.dp))
            sensitiveModes().forEach { (mode, label) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = !busy) {
                        if (mode != prefs.mode) save(mapOf("sensitive_mode" to mode))
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == prefs.mode, onClick = null, enabled = !busy, colors = RadioButtonDefaults.colors(selectedColor = glimpse.accent))
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.sensitive_terms), style = MaterialTheme.typography.titleSmall, color = glimpse.text)
            val st = status
            if (st != null && st.terms.isEmpty()) {
                Text(stringResource(R.string.sensitive_no_terms), style = MaterialTheme.typography.bodySmall, color = glimpse.muted)
            }
            st?.terms?.forEach { term ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                        onDismiss()
                        app.search(term.q)
                    }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sensitive_term_quoted, term.q), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (term.stale) stringResource(R.string.sensitive_term_stale) else res.getQuantityString(R.plurals.sensitive_term_count, term.count, term.count),
                            style = MaterialTheme.typography.bodySmall,
                            color = glimpse.muted,
                        )
                    }
                    IconButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                status = app.api.removeSensitiveTerm(term.q)
                                app.sensitiveModeChanged(prefs)
                            } catch (e: Exception) {
                                app.notices.show(failed.replace("%s", e.message ?: e.javaClass.simpleName))
                            } finally {
                                busy = false
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.sensitive_remove_term, term.q), tint = glimpse.muted)
                    }
                }
            }
            if (st != null) {
                Spacer(Modifier.height(4.dp))
                Text(res.getQuantityString(R.plurals.sensitive_count, st.flagged, st.flagged), style = MaterialTheme.typography.bodySmall, color = glimpse.muted)
                if (st.added + st.removed > 0) {
                    Text(stringResource(R.string.sensitive_marks_moved, st.added, st.removed), style = MaterialTheme.typography.bodySmall, color = glimpse.muted)
                }
                st.error?.let { Text(stringResource(R.string.sensitive_failed, it), style = MaterialTheme.typography.bodySmall, color = glimpse.muted) }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.sensitive_marks_hint), style = MaterialTheme.typography.bodySmall, color = glimpse.muted)

            Spacer(Modifier.height(8.dp))
            SwitchRow(stringResource(R.string.sensitive_badge), prefs.badge, enabled = !busy) { save(mapOf("sensitive_badge" to it)) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = glimpse.accent))
    }
}
