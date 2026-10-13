package com.infraviored.glimpse.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.data.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A list Glimpse hands out page by page (timeline, search results, a person's pictures).
 * A reload drops a request still running, so an old answer is never appended to a new list.
 */
class Paged(
    private val scope: CoroutineScope,
    private val fetch: suspend (page: Int) -> Page,
) {
    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Whether the first page has arrived (an empty list before that is not "no results"). */
    var loaded by mutableStateOf(false)
        private set
    var lastPage by mutableStateOf<Page?>(null)
        private set

    private var next: Int? = 1
    private var job: Job? = null

    val hasMore: Boolean get() = next != null

    fun loadMore() {
        val page = next ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            loading = true
            error = null
            try {
                // The server filters pages by what this user may see; a few empty ones in a
                // row are skipped here so the list does not stall without saying why
                var result = fetch(page)
                var empty = 0
                while (result.items.isEmpty() && result.nextPage != null && ++empty < EMPTY_PAGES) {
                    result = fetch(result.nextPage!!)
                }
                items = items + result.items
                next = result.nextPage
                lastPage = result
                loaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    fun reload() {
        job?.cancel()
        job = null
        items = emptyList()
        next = 1
        loaded = false
        loading = false
        loadMore()
    }

    /** Pictures that left this list (deleted). */
    fun remove(fileIds: Set<Long>) {
        items = items.filterNot { it.fileId in fileIds }
    }
}

private const val EMPTY_PAGES = 5

/** Load something once, with the same error handling. */
fun <T> CoroutineScope.loadInto(onError: (String) -> Unit, block: suspend () -> T, onResult: (T) -> Unit): Job = launch {
    try {
        onResult(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e.message ?: e.javaClass.simpleName)
    }
}
