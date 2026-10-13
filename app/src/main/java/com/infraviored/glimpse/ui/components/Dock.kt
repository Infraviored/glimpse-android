package com.infraviored.glimpse.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.R
import com.infraviored.glimpse.ui.theme.Palette
import com.infraviored.glimpse.ui.theme.glimpse

data class Tab(val label: String)

class DockAction(val icon: ImageVector, val label: String, val subtitle: String? = null, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The bottom of every main screen, where the thumb is: the lens (search) above the tabs.
 * While pictures are selected it turns into their actions.
 */
@Composable
fun Dock(
    tabs: List<Tab>,
    current: Int,
    onTab: (Int) -> Unit,
    onLens: () -> Unit,
    selectedCount: Int,
    actions: List<DockAction>,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    more: List<DockAction> = emptyList(),
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, glimpse.background.copy(alpha = 0.92f), glimpse.background), startY = 0f, endY = 120f))
            .navigationBarsPadding()
            .padding(top = 28.dp, bottom = 6.dp),
    ) {
        AnimatedContent(
            targetState = selectedCount > 0,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 3 }) togetherWith (fadeOut() + slideOutVertically { it / 3 })
            },
            label = "dock",
        ) { selecting ->
            if (selecting) {
                SelectionDock(selectedCount, actions, more, onClearSelection)
            } else {
                Column {
                    Lens(onLens, Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        tabs.forEachIndexed { i, tab -> TabLabel(tab.label, i == current) { onTab(i) } }
                    }
                }
            }
        }
    }
}

/** The search field's stand-in: a pill with the lens ring, opening the search screen. */
@Composable
fun Lens(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(glimpse.raised)
            .border(1.dp, glimpse.line, RoundedCornerShape(27.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LensMark(Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.lens_hint), color = glimpse.muted, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The ring of the app icon, drawn: blue into violet, with the apricot spark. */
@Composable
fun LensMark(modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            val stroke = size.minDimension * 0.15f
            val r = size.minDimension * 0.34f
            val c = Offset(size.width * 0.44f, size.height * 0.5f)
            drawCircle(Palette.Lens, radius = r, center = c, style = Stroke(stroke))
            drawCircle(Palette.Apricot, radius = size.minDimension * 0.09f, center = Offset(size.width * 0.86f, size.height * 0.16f))
        },
    )
}

@Composable
private fun TabLabel(label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { selected = active; role = Role.Tab }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (active) glimpse.text else glimpse.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (active) glimpse.accent else Color.Transparent))
    }
}

@Composable
private fun SelectionDock(count: Int, actions: List<DockAction>, more: List<DockAction>, onClear: () -> Unit) {
    var moreOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(glimpse.raised)
            .border(1.dp, glimpse.line, RoundedCornerShape(28.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, stringResource(R.string.selection_clear), tint = glimpse.text) }
        Text(
            pluralStringResource(R.plurals.selected_count, count, count),
            style = MaterialTheme.typography.titleMedium,
            color = glimpse.text,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions.forEach { a ->
            IconButton(onClick = a.onClick, enabled = a.enabled) { Icon(a.icon, a.label, tint = if (a.enabled) glimpse.text else glimpse.muted) }
        }
        if (more.isNotEmpty()) {
            IconButton(onClick = { moreOpen = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more), tint = glimpse.text) }
        }
    }
    if (moreOpen) {
        GlimpseSheet(onDismiss = { moreOpen = false }) {
            SheetTitle(pluralStringResource(R.plurals.selected_count, count, count))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                more.forEach { a ->
                    SheetRow(a.icon, a.label, a.subtitle, enabled = a.enabled) {
                        moreOpen = false
                        a.onClick()
                    }
                }
            }
        }
    }
}
