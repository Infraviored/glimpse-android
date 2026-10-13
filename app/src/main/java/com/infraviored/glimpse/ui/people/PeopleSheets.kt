package com.infraviored.glimpse.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.FaceTarget
import com.infraviored.glimpse.data.Person
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.components.ChoiceRow
import com.infraviored.glimpse.ui.components.GlimpseSheet
import com.infraviored.glimpse.ui.components.PillField
import com.infraviored.glimpse.ui.components.SheetButtons
import com.infraviored.glimpse.ui.components.SheetTitle
import com.infraviored.glimpse.ui.count
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.theme.glimpse

@Composable
private fun Face(id: String) {
    AsyncImage(
        model = LocalApp.current.api.faceUrl(id),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(40.dp).clip(CircleShape).background(glimpse.raised).photoBlur(),
    )
}

/** The person that stays when merging: the named one with the most pictures. */
fun mergeTarget(people: List<Person>): Person? =
    people.sortedWith(compareByDescending<Person> { it.name.isNotBlank() }.thenByDescending { it.count }).firstOrNull()

/** Which one stays? The others go into it, with all their pictures. */
@Composable
fun MergeSheet(people: List<Person>, busy: Boolean, onDismiss: () -> Unit, onMerge: (target: String, others: List<String>, name: String) -> Unit) {
    val best = remember(people) { mergeTarget(people) } ?: return
    var target by remember { mutableStateOf(best.id) }
    var name by remember { mutableStateOf(best.name) }
    GlimpseSheet(onDismiss) {
        SheetTitle(stringResource(R.string.merge_title), stringResource(R.string.merge_text))
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            people.forEach { p ->
                ChoiceRow(
                    title = p.name.ifBlank { stringResource(R.string.unnamed) },
                    subtitle = pluralStringResource(R.plurals.pictures, p.count, count(p.count)),
                    chosen = p.id == target,
                    leading = { Face(p.id) },
                ) {
                    target = p.id
                    if (p.name.isNotBlank()) name = p.name
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PillField(name, { name = it }, stringResource(R.string.person_name))
        SheetButtons(
            cancel = stringResource(R.string.cancel),
            confirm = stringResource(R.string.merge_action),
            onCancel = onDismiss,
            onConfirm = { onMerge(target, people.map { it.id }.filter { it != target }, name) },
            enabled = people.size >= 2,
            busy = busy,
        )
    }
}

private enum class NotMode { OTHER, UNNAMED, NOFACE }

/** "Not Tanja": where the faces of [person] in the chosen pictures go instead. */
@Composable
fun NotPersonSheet(person: Person, count: Int, others: List<Person>, busy: Boolean, onDismiss: () -> Unit, onApply: (FaceTarget) -> Unit) {
    var mode by remember { mutableStateOf(NotMode.OTHER) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<FaceTarget?>(null) }
    val personName = person.name.ifBlank { stringResource(R.string.unnamed) }
    val candidates = remember(others, query) {
        val q = query.trim()
        others.filter { it.name.isNotBlank() && it.id != person.id && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }.take(6)
    }
    val exact = others.any { it.name.equals(query.trim(), ignoreCase = true) }
    val target: FaceTarget? = when (mode) {
        NotMode.UNNAMED -> FaceTarget.Unnamed
        NotMode.NOFACE -> FaceTarget.NotAFace
        NotMode.OTHER -> picked
    }
    GlimpseSheet(onDismiss) {
        SheetTitle(
            stringResource(R.string.not_person_title, personName),
            pluralStringResource(R.plurals.not_person_text, count, count, personName),
        )
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            ChoiceRow(stringResource(R.string.not_person_other), mode == NotMode.OTHER) { mode = NotMode.OTHER }
            if (mode == NotMode.OTHER) {
                Column(Modifier.padding(start = 36.dp)) {
                    PillField(query, { query = it; picked = null }, stringResource(R.string.not_person_who), leading = Icons.Outlined.Search)
                    Spacer(Modifier.height(6.dp))
                    candidates.forEach { p ->
                        ChoiceRow(p.name, picked == FaceTarget.Person(p.id), leading = { Face(p.id) }) { picked = FaceTarget.Person(p.id) }
                    }
                    if (query.isNotBlank() && !exact) {
                        val new = FaceTarget.NewName(query.trim())
                        ChoiceRow(stringResource(R.string.not_person_new, query.trim()), picked == new) { picked = new }
                    }
                }
            }
            ChoiceRow(
                stringResource(R.string.not_person_unnamed),
                mode == NotMode.UNNAMED,
                subtitle = if (mode == NotMode.UNNAMED) stringResource(R.string.not_person_unnamed_hint) else null,
            ) { mode = NotMode.UNNAMED }
            ChoiceRow(
                stringResource(R.string.not_person_noface),
                mode == NotMode.NOFACE,
                subtitle = if (mode == NotMode.NOFACE) stringResource(R.string.not_person_noface_hint) else null,
            ) { mode = NotMode.NOFACE }
        }
        SheetButtons(
            cancel = stringResource(R.string.cancel),
            confirm = stringResource(R.string.not_person_apply),
            onCancel = onDismiss,
            onConfirm = { target?.let(onApply) },
            enabled = target != null,
            busy = busy,
        )
    }
}

