package com.infraviored.glimpse.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.Debug

/** Debug builds: photos blurred for design screenshots ([Debug.blurPhotos]). */
fun Modifier.photoBlur(): Modifier = if (Debug.blurPhotos) this.blur(18.dp) else this
