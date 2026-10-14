package com.infraviored.glimpse.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.ui.components.Notices

/** Scopes the picture last viewed in the viewer to the grid that opened it. */
data class RestoreTarget(
    val gridKey: String,
    val fileId: Long,
)

/** What the viewer pages through: the list the picture was opened from. */
class ViewerRequest(
    val items: List<Item>,
    val start: Int,
    /** Lets the viewer page further than what is loaded (timeline, results). */
    val source: Paged? = null,
    /** Scopes restore-on-return to the grid that opened the viewer. */
    val gridKey: String? = null,
)

/** Navigation and shared state the screens reach through [LocalApp]. */
@Stable
class AppState(
    val api: Api,
    val navigate: (String) -> Unit,
    val back: () -> Unit,
    val logout: () -> Unit,
) {
    var viewer by mutableStateOf<ViewerRequest?>(null)

    /** The picture the viewer showed last; the grid it was opened from scrolls to it on return. */
    var lastViewed by mutableStateOf<RestoreTarget?>(null)

    /** Pictures deleted anywhere (or hidden by the locked private area), so every list can drop them. */
    var deleted by mutableStateOf<Set<Long>>(emptySet())

    /**
     * Goes up when something changed that lists cannot patch themselves (people renamed or
     * merged, files moved, the private area locked or unlocked): they load again.
     */
    var version by mutableIntStateOf(0)
        private set

    fun changed() {
        version++
    }

    /** The private area as last heard from the server; null until asked. */
    var privateArea by mutableStateOf<com.infraviored.glimpse.data.PrivateState?>(null)

    /**
     * Goes up when the private area is locked or unlocked, or the sensitive mode changes:
     * everything visible changes.
     */
    var privateVersion by mutableIntStateOf(0)
        private set

    /** Pictures made private while locked: gone from view until unlocking. */
    private var hiddenPrivate = emptySet<Long>()

    fun hidePrivate(fileIds: Collection<Long>) {
        hiddenPrivate = hiddenPrivate + fileIds
        deleted = deleted + fileIds
    }

    fun privacyChanged(state: com.infraviored.glimpse.data.PrivateState) {
        privateArea = state
        if (state.unlocked) {
            deleted = deleted - hiddenPrivate
            hiddenPrivate = emptySet()
        }
        privateVersion++
        version++
    }

    /** Sensitive pictures: the admin allows them (the user can turn them on). */
    var sensitiveAvailable by mutableStateOf(false)

    /** Sensitive pictures: null while off (not allowed by the admin, or not turned on by the user). */
    var sensitive by mutableStateOf<com.infraviored.glimpse.data.SensitivePrefs?>(null)

    /** Marked by hand in this session, so tiles change before the lists load again. */
    var sensitiveMarks by mutableStateOf<Map<Long, Boolean>>(emptyMap())

    fun isSensitive(item: Item): Boolean = sensitiveMarks[item.fileId] ?: item.sensitive

    /** Sensitive settings changed what may be shown: everything loads again. */
    fun sensitiveModeChanged(prefs: com.infraviored.glimpse.data.SensitivePrefs) {
        sensitive = prefs.takeIf { it.enabled }
        sensitiveMarks = emptyMap()
        privateVersion++
        version++
    }

    /** Whether the user is a Glimpse admin; null while unknown (admin actions then show and may get a 403). */
    var isAdmin by mutableStateOf<Boolean?>(null)

    /** Short messages at the bottom, with Undo where there is one. */
    val notices = Notices()

    fun open(items: List<Item>, item: Item, source: Paged? = null, gridKey: String? = null) {
        viewer = ViewerRequest(items, items.indexOf(item).coerceAtLeast(0), source, gridKey)
        navigate(Routes.VIEWER)
    }

    fun search(q: String = "", person: String = "", similar: String = "", label: String = "") =
        navigate(Routes.search(q, person, similar, label))

    fun person(id: String) = navigate(Routes.person(id))

    fun folder(path: String) = navigate(Routes.folder(path))

    fun duplicates() = navigate(Routes.DUPLICATES)

    fun trainSensitive() = navigate(Routes.SENSITIVE)
}

object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer"
    const val SEARCH = "search?q={q}&person={person}&similar={similar}&label={label}"
    const val PERSON = "person/{id}"
    const val FOLDER = "folder?path={path}"
    const val DUPLICATES = "duplicates"
    const val SENSITIVE = "sensitive"

    private fun e(s: String) = android.net.Uri.encode(s)
    fun search(q: String, person: String, similar: String, label: String) =
        "search?q=${e(q)}&person=${e(person)}&similar=${e(similar)}&label=${e(label)}"
    fun person(id: String) = "person/${e(id)}"
    fun folder(path: String) = "folder?path=${e(path)}"
}

val LocalApp = compositionLocalOf<AppState> { error("no app state") }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalShared = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimated = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The thumbnail in a grid and the picture in the viewer are one element moving between screens. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.photoElement(fileId: Long): Modifier {
    val shared = LocalShared.current ?: return this
    val animated = LocalAnimated.current ?: return this
    return with(shared) {
        this@photoElement.sharedElement(
            sharedContentState = rememberSharedContentState("photo-$fileId"),
            animatedVisibilityScope = animated,
        )
    }
}
