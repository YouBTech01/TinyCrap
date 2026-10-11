package com.tinycrap.app.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinycrap.app.TinyCrapApp
import com.tinycrap.app.data.DownloadTask
import com.tinycrap.app.data.LibraryItem
import com.tinycrap.app.data.MediaKind
import com.tinycrap.app.data.Platform
import com.tinycrap.app.data.TaskState
import com.tinycrap.app.ui.UiMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    Newest("Newest first"), Oldest("Oldest first"), Largest("Largest first"), Name("Name (A–Z)")
}

data class Filters(
    val query: String = "",
    val kind: MediaKind? = null,
    val platform: Platform? = null,
    val sort: SortOrder = SortOrder.Newest,
) {
    val isActive: Boolean get() = query.isNotBlank() || kind != null || platform != null
}

data class DownloadsUi(
    val items: List<LibraryItem> = emptyList(),
    val total: Int = 0,
    val totalBytes: Long = 0,
    val filters: Filters = Filters(),
    val selected: Set<String> = emptySet(),
)

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {
    private val tc = app as TinyCrapApp
    private val library = tc.library
    private val files = tc.files
    private val downloads = tc.downloads

    private val filters = MutableStateFlow(Filters())
    private val selection = MutableStateFlow<Set<String>>(emptySet())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val ui: StateFlow<DownloadsUi> = combine(library.items, filters, selection) { all, f, sel ->
        val q = f.query.trim()
        val filtered = all.asSequence()
            .filter { f.kind == null || it.kind == f.kind }
            .filter { f.platform == null || it.platform == f.platform }
            .filter {
                q.isEmpty() || it.title.contains(q, ignoreCase = true) ||
                    (it.uploader?.contains(q, ignoreCase = true) == true) ||
                    it.filename.contains(q, ignoreCase = true)
            }
            .toList()
        val sorted = when (f.sort) {
            SortOrder.Newest -> filtered.sortedByDescending { it.createdAt }
            SortOrder.Oldest -> filtered.sortedBy { it.createdAt }
            SortOrder.Largest -> filtered.sortedByDescending { it.size }
            SortOrder.Name -> filtered.sortedBy { it.title.lowercase() }
        }
        val ids = all.mapTo(HashSet()) { it.id }
        DownloadsUi(sorted, all.size, all.sumOf { it.size }, f, sel.filter { it in ids }.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUi())

    /** Queued, running and failed downloads (finished ones move to the file list). */
    val tasks: StateFlow<List<DownloadTask>> = downloads.tasks
        .map { list -> list.filter { it.active || it.state == TaskState.Failed }.sortedBy { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeCount: StateFlow<Int> = downloads.tasks.map { list -> list.count { it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ---- filters --------------------------------------------------------------------------

    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun toggleKind(kind: MediaKind) = filters.update { it.copy(kind = if (it.kind == kind) null else kind) }
    fun togglePlatform(p: Platform) = filters.update { it.copy(platform = if (it.platform == p) null else p) }
    fun clearFilters() = filters.update { it.copy(query = "", kind = null, platform = null) }
    fun setSort(order: SortOrder) = filters.update { it.copy(sort = order) }

    // ---- selection ------------------------------------------------------------------------

    fun toggleSelected(id: String) = selection.update { if (id in it) it - id else it + id }
    fun selectAllShown() = selection.update { ui.value.items.map { i -> i.id }.toSet() }
    fun clearSelection() { selection.value = emptySet() }

    // ---- files ----------------------------------------------------------------------------

    /** Forgets files that were deleted or moved outside Tiny Crap. */
    fun refresh() {
        viewModelScope.launch {
            val missing = library.items.value.filterNot { files.exists(it.uri) }.map { it.id }
            if (missing.isNotEmpty()) library.removeAll(missing)
        }
    }

    fun deleteFiles(ids: Collection<String>) {
        viewModelScope.launch {
            val items = library.items.value.filter { it.id in ids }
            var failed = 0
            items.forEach { item ->
                // A file the user already deleted elsewhere counts as gone.
                if (files.delete(item.uri) || !files.exists(item.uri)) library.remove(item.id) else failed++
            }
            selection.value = emptySet()
            _messages.tryEmit(
                UiMessage(
                    when {
                        failed > 0 -> "Could not delete $failed file${if (failed == 1) "" else "s"}."
                        items.size == 1 -> "File deleted"
                        else -> "${items.size} files deleted"
                    }
                )
            )
        }
    }

    fun removeFromList(id: String) {
        library.remove(id)
        _messages.tryEmit(UiMessage("Removed from the list. The file is still on your phone."))
    }

    // ---- queue ----------------------------------------------------------------------------

    fun cancel(id: String) = downloads.cancel(id)
    fun retry(id: String) = downloads.retry(id)
    fun cancelAll() = downloads.cancelAll()
}
