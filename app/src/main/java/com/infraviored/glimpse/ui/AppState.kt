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

/** What the viewer pages through: the list the picture was opened from. */
class ViewerRequest(
    val items: List<Item>,
    val start: Int,
    /** Lets the viewer page further than what is loaded (timeline, results). */
    val source: Paged? = null,
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

    /** Short messages at the bottom, with Undo where there is one. */
    val notices = Notices()

    fun open(items: List<Item>, item: Item, source: Paged? = null) {
        viewer = ViewerRequest(items, items.indexOf(item).coerceAtLeast(0), source)
        navigate(Routes.VIEWER)
    }

    fun search(q: String = "", person: String = "", similar: String = "", label: String = "") =
        navigate(Routes.search(q, person, similar, label))

    fun person(id: String) = navigate(Routes.person(id))

    fun folder(path: String) = navigate(Routes.folder(path))

    fun duplicates() = navigate(Routes.DUPLICATES)
}

object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer"
    const val SEARCH = "search?q={q}&person={person}&similar={similar}&label={label}"
    const val PERSON = "person/{id}"
    const val FOLDER = "folder?path={path}"
    const val DUPLICATES = "duplicates"

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
