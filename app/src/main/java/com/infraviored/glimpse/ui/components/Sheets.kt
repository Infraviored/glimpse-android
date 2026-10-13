package com.infraviored.glimpse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.ui.theme.Palette
import com.infraviored.glimpse.ui.theme.glimpse

/** The app's bottom sheet: rounded, in the night blue, opened all the way. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun GlimpseSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glimpse.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        // A sheet is its own window: a shared element in it cannot travel to the viewer
        androidx.compose.runtime.CompositionLocalProvider(com.infraviored.glimpse.ui.LocalShared provides null) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), content = content)
        }
    }
}

/** A sheet's heading, in the display type. */
@Composable
fun SheetTitle(text: String, subtitle: String? = null) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = glimpse.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = glimpse.muted)
    }
    Spacer(Modifier.height(14.dp))
}

/** One choice in a sheet: an apricot icon on a raised tile, a title and maybe a line below. */
@Composable
fun SheetRow(icon: ImageVector, title: String, subtitle: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(glimpse.raised), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = glimpse.accentText, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = glimpse.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = glimpse.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** The one thing a sheet is for: a pill in the lens gradient. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    Box(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(if (enabled) Palette.Lens else SolidColor(glimpse.raised))
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color.White)
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) Color.White else glimpse.muted)
        }
    }
}

/** Second choice next to [PrimaryButton]: an outlined pill. */
@Composable
fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .border(1.dp, glimpse.line, RoundedCornerShape(25.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = glimpse.text)
    }
}

/** A text field as a raised pill, like the people filter. */
@Composable
fun PillField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    onDone: (() -> Unit)? = null,
    leading: ImageVector? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(glimpse.raised)
            .border(1.dp, glimpse.line, RoundedCornerShape(25.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = glimpse.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = glimpse.muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = glimpse.text),
                cursorBrush = SolidColor(glimpse.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A radio choice in a sheet: the apricot dot marks the chosen one. */
@Composable
fun ChoiceRow(title: String, chosen: Boolean, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (chosen) glimpse.accent else glimpse.muted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (chosen) Box(Modifier.size(11.dp).clip(CircleShape).background(glimpse.accent))
        }
        Spacer(Modifier.width(14.dp))
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = glimpse.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = glimpse.muted) }
        }
    }
}

/** Cancel and the action, side by side at the bottom of a sheet. */
@Composable
fun SheetButtons(cancel: String, confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit, enabled: Boolean = true, busy: Boolean = false) {
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth()) {
        QuietButton(cancel, onCancel, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        PrimaryButton(confirm, onConfirm, Modifier.weight(1f), enabled = enabled, busy = busy)
    }
}
