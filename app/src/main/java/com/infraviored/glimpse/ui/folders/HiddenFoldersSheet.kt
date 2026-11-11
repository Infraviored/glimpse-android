package com.infraviored.glimpse.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.errorText
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.components.GlimpseSheet
import com.infraviored.glimpse.ui.components.SheetTitle
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HiddenFoldersSheet(onDismiss: () -> Unit, scope: CoroutineScope = rememberCoroutineScope()) {
    val app = LocalApp.current
    val context = LocalContext.current
    var folders by remember { mutableStateOf<List<String>?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        folders = runCatching { app.api.hiddenFolders().folders }.getOrNull() ?: emptyList()
    }

    GlimpseSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle(stringResource(R.string.hidden_folders_title))
            val list = folders
            if (list == null) {
                // Initial load
            } else if (list.isEmpty()) {
                Text(
                    stringResource(R.string.hidden_folders_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = glimpse.muted,
                )
            } else {
                list.forEach { folder ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onDismiss()
                                app.folder(folder)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(glimpse.raised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.FolderOff, null, tint = glimpse.accentText, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                folder.substringAfterLast('/').ifEmpty { folder },
                                style = MaterialTheme.typography.titleMedium,
                                color = glimpse.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                folder,
                                style = MaterialTheme.typography.bodySmall,
                                color = glimpse.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            enabled = !busy,
                            onClick = {
                                if (busy) return@IconButton
                                busy = true
                                scope.launch {
                                    try {
                                        val updated = list.filter { it != folder }
                                        val res = withContext(NonCancellable) {
                                            app.api.setHiddenFolders(updated)
                                        }
                                        folders = res.folders
                                        app.hiddenFoldersChanged()
                                    } catch (e: Exception) {
                                        val msg = (e as? com.infraviored.glimpse.data.ApiException)?.message ?: errorText(e)
                                        app.notices.show(context.getString(R.string.action_failed, msg))
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.hidden_folder_remove),
                                tint = glimpse.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
