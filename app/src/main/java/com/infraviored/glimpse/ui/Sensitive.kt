package com.infraviored.glimpse.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.School
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.SensitivePrefs
import com.infraviored.glimpse.data.SensitiveStatus
import com.infraviored.glimpse.data.errorText
import com.infraviored.glimpse.ui.components.GlimpseSheet
import com.infraviored.glimpse.ui.components.SheetTitle
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** [reload]: false for what only changes the look (the mark), so the lists keep their place. */
    fun save(changes: Map<String, Any>, reload: Boolean = true) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val saved = app.api.saveSensitive(changes)
                if (reload) app.sensitiveModeChanged(saved) else app.sensitive = saved.takeIf { it.enabled }
            } catch (e: Exception) {
                app.notices.show(failed.replace("%s", errorText(e)))
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

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                    onDismiss()
                    app.trainSensitive()
                }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.School, null, tint = glimpse.accentText)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.trainer_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.trainer_subtitle), style = MaterialTheme.typography.bodySmall, color = glimpse.muted)
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
                                app.notices.show(failed.replace("%s", errorText(e)))
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
                st.change?.takeIf { it.added + it.removed > 0 }?.let { c ->
                    Text(stringResource(R.string.sensitive_marks_moved, c.added, c.removed), style = MaterialTheme.typography.bodySmall, color = glimpse.muted)
                }
                st.error?.let { Text(stringResource(R.string.sensitive_failed, it), style = MaterialTheme.typography.bodySmall, color = glimpse.muted) }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.sensitive_marks_hint), style = MaterialTheme.typography.bodySmall, color = glimpse.muted)

            Spacer(Modifier.height(8.dp))
            SwitchRow(stringResource(R.string.sensitive_badge), prefs.badge, enabled = !busy) { save(mapOf("sensitive_badge" to it), reload = false) }
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

/** Material Design Icons' shields: what the sensitive filter lets through. */
private val ShieldOffOutline: ImageVector by lazy { svgIcon("ShieldOffOutline", "M1,4.27L3,6.27V11C3,16.55 6.84,21.74 12,23C13.87,22.54 15.57,21.56 16.97,20.24L19.23,22.5L20.5,21.22L2.28,3L1,4.27M12,21C8.25,20 5,15.54 5,11.22V8.27L15.59,18.86C14.53,19.89 13.3,20.65 12,21M21,5V11C21,13.28 20.35,15.5 19.23,17.4L17.77,15.95C18.54,14.5 19,12.84 19,11.22V6.3L12,3.18L7.16,5.34L5.65,3.82L12,1L21,5Z") }
private val ShieldAlertOutline: ImageVector by lazy { svgIcon("ShieldAlertOutline", "M21,11C21,16.55 17.16,21.74 12,23C6.84,21.74 3,16.55 3,11V5L12,1L21,5V11M12,21C15.75,20 19,15.54 19,11.22V6.3L12,3.18L5,6.3V11.22C5,15.54 8.25,20 12,21M11,7H13V13H11V7M11,15H13V17H11V15Z") }
private val ShieldCheckOutline: ImageVector by lazy { svgIcon("ShieldCheckOutline", "M21,11C21,16.55 17.16,21.74 12,23C6.84,21.74 3,16.55 3,11V5L12,1L21,5V11M12,21C15.75,20 19,15.54 19,11.22V6.3L12,3.18L5,6.3V11.22C5,15.54 8.25,20 12,21M10,17L6,13L7.41,11.59L10,14.17L16.59,7.58L18,9") }

/** Material Design Icons' folder-off-outline: ignore a folder. */
val FolderOffOutline: ImageVector by lazy { svgIcon("FolderOffOutline", "M2.39 1.73L1.11 3L2.64 4.53C2.25 4.9 2 5.42 2 6V18C2 19.11 2.9 20 4 20H18.11L20.84 22.73L22.11 21.46L2.39 1.73M4 18V8H6.11L16.11 18H4M11.2 8L7.2 4H10L12 6H20C21.1 6 22 6.89 22 8V18C22 18.24 21.96 18.47 21.88 18.68L20 16.8V8H11.2Z") }

private fun svgIcon(name: String, d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(addPathNodes(d), fill = SolidColor(Color.Black)).build()

/** The shield says what is shown: all (off), without sensitive (check), only sensitive (alert). */
fun sensitiveModeIcon(mode: String?): ImageVector = when (mode) {
    "hide" -> ShieldCheckOutline
    "only" -> ShieldAlertOutline
    else -> ShieldOffOutline
}

/**
 * Next to the title of a page: sensitive pictures can show here. A tap turns them off; the safe
 * shield then says it worked and fades out. The account menu is the way back. It waits for the
 * server, so a tap that fails leaves it standing, and scrolling it away must not cancel the change.
 */
@Composable
fun SensitiveShield(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val exposed = app.sensitive?.mode.let { it == "all" || it == "only" }
    var shown by remember { mutableStateOf(exposed) }
    var fading by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (fading) 0f else 1f, tween(SHIELD_FADE_MS), label = "shield")
    LaunchedEffect(exposed) {
        fading = false
        if (exposed) {
            shown = true
        } else if (shown) {
            delay(SHIELD_SAFE_MS)
            fading = true
            delay(SHIELD_FADE_MS.toLong())
            shown = false
        }
    }
    if (!shown) return
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.action_failed, "%s")
    var busy by remember { mutableStateOf(false) }
    Box(
        modifier.size(36.dp).alpha(alpha).clip(CircleShape).clickable(enabled = exposed && !busy) {
            busy = true
            scope.launch {
                withContext(NonCancellable) {
                    try {
                        app.sensitiveModeChanged(app.api.saveSensitive(mapOf("sensitive_mode" to "hide")))
                    } catch (e: Exception) {
                        app.notices.show(failed.replace("%s", errorText(e)))
                    } finally {
                        busy = false
                    }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (exposed) ShieldOffOutline else ShieldCheckOutline,
            stringResource(if (exposed) R.string.sensitive_hide else R.string.sensitive_hide_short),
            tint = if (exposed) glimpse.muted else glimpse.accentText,
            modifier = Modifier.size(18.dp),
        )
    }
}

private const val SHIELD_SAFE_MS = 1200L
private const val SHIELD_FADE_MS = 400

/** The shields again: a sensitive picture, and one that is not. */
val SensitiveIcon: ImageVector get() = ShieldAlertOutline
val NotSensitiveIcon: ImageVector get() = ShieldCheckOutline
