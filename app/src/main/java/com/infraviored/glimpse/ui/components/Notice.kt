package com.infraviored.glimpse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.delay

/** A short message at the bottom, maybe with one action (Undo). */
class Notice(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null, val millis: Long = if (action != null) 8000 else 3500) {
    val id = System.nanoTime()
}

class Notices {
    var current by mutableStateOf<Notice?>(null)
        private set

    fun show(notice: Notice) {
        current = notice
    }

    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) = show(Notice(text, action, onAction))

    fun dismiss(notice: Notice) {
        if (current?.id == notice.id) current = null
    }
}

/** The notice as a raised pill above the dock; the action in apricot. */
@Composable
fun NoticeHost(notices: Notices, modifier: Modifier = Modifier) {
    val notice = notices.current
    LaunchedEffect(notice?.id) {
        notice ?: return@LaunchedEffect
        delay(notice.millis)
        notices.dismiss(notice)
    }
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val shown = notice ?: return@AnimatedVisibility
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 150.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(glimpse.raised)
                .border(1.dp, glimpse.line, RoundedCornerShape(22.dp))
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                shown.text,
                style = MaterialTheme.typography.bodyMedium,
                color = glimpse.text,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            )
            if (shown.action != null) {
                Text(
                    shown.action,
                    style = MaterialTheme.typography.titleMedium,
                    color = glimpse.accentText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            notices.dismiss(shown)
                            shown.onAction?.invoke()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}
