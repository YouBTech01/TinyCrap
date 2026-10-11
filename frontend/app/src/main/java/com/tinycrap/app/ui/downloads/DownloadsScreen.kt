package com.tinycrap.app.ui.downloads

import com.tinycrap.app.ui.components.pressScale
import com.tinycrap.app.ui.components.enterOnce
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinycrap.app.data.LibraryItem
import com.tinycrap.app.data.MediaKind
import com.tinycrap.app.data.Platform
import com.tinycrap.app.data.formatBytes
import com.tinycrap.app.ui.components.SectionLabel
import com.tinycrap.app.ui.components.MediaThumb
import com.tinycrap.app.ui.components.TaskCard
import com.tinycrap.app.ui.util.openMedia
import com.tinycrap.app.ui.util.relativeTime
import com.tinycrap.app.ui.util.shareMedia

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    vm: DownloadsViewModel,
    visible: Boolean,
    contentPadding: PaddingValues,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var confirmDelete by remember { mutableStateOf<List<LibraryItem>?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    val selecting = ui.selected.isNotEmpty()

    // Forget files that were removed in other apps whenever this tab comes into view.
    LaunchedEffect(visible) { if (visible) vm.refresh() }
    BackHandler(enabled = visible && selecting) { vm.clearSelection() }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selecting) {
                TopAppBar(
                    title = { Text("${ui.selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) { Icon(Icons.Rounded.Close, contentDescription = "Cancel selection") }
                    },
                    actions = {
                        IconButton(onClick = vm::selectAllShown) { Icon(Icons.Rounded.SelectAll, contentDescription = "Select all") }
                        IconButton(onClick = { confirmDelete = ui.items.filter { it.id in ui.selected } }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            } else {
                TopAppBar(
                    title = { Text("Downloaded") },
                    actions = {
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        leadingIcon = { RadioButton(selected = ui.filters.sort == order, onClick = null) },
                                        onClick = { vm.setSort(order); sortMenu = false },
                                    )
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "search") {
                    SearchField(
                        value = ui.filters.query,
                        onValueChange = vm::setQuery,
                        summary = null,
                    )
                }
                item(key = "chips") {
                    FilterChips(ui.filters, vm)
                }

                if (tasks.isNotEmpty()) {
                    item(key = "active-title") {
                        SectionLabel("In progress · ${tasks.size}") {
                            if (tasks.any { it.active }) TextButton(onClick = vm::cancelAll) { Text("Cancel all") }
                        }
                    }
                    items(tasks, key = { "task-${it.id}" }) { task ->
                        TaskCard(
                            modifier = Modifier.animateItem(),
                            task = task,
                            onCancel = { vm.cancel(task.id) },
                            onRetry = { vm.retry(task.id) },
                            onDismiss = { vm.cancel(task.id) },
                        )
                    }
                }

                if (ui.items.isEmpty()) {
                    item(key = "empty", contentType = "empty") { EmptyState(filtered = ui.filters.isActive, hasAny = ui.total > 0, onClear = vm::clearFilters) }
                } else {
                    itemsIndexed(ui.items, key = { _, it -> it.id }) { index, item ->
                        FileCard(
                            modifier = Modifier.animateItem().enterOnce(item.id, index),
                            item = item,
                            selected = item.id in ui.selected,
                            selecting = selecting,
                            onClick = {
                                if (selecting) vm.toggleSelected(item.id) else openMedia(context, item.uri, item.mime)
                            },
                            onLongClick = { vm.toggleSelected(item.id) },
                            onShare = { shareMedia(context, item.uri, item.mime, item.title) },
                            onRemoveFromList = { vm.removeFromList(item.id) },
                            onDelete = { confirmDelete = listOf(item) },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (list.size == 1) "Delete this file?" else "Delete ${list.size} files?") },
            text = {
                Text(
                    if (list.size == 1) "\"${list.first().title}\" will be removed from your phone."
                    else "These files will be removed from your phone."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteFiles(list.map { it.id }); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

// ---- search and filters -----------------------------------------------------------------------------

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, summary: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search") },
            singleLine = true,
            shape = CircleShape,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun FilterChips(f: Filters, vm: DownloadsViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = f.kind == null && f.platform == null, onClick = vm::clearFilters, label = { Text("All") })
        }
        item {
            FilterChip(
                selected = f.kind == MediaKind.Video, onClick = { vm.toggleKind(MediaKind.Video) },
                label = { Text("Videos") },
                leadingIcon = { Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        item {
            FilterChip(
                selected = f.kind == MediaKind.Audio, onClick = { vm.toggleKind(MediaKind.Audio) },
                label = { Text("Music") },
                leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        items(Platform.entries) { p ->
            FilterChip(selected = f.platform == p, onClick = { vm.togglePlatform(p) }, label = { Text(p.label) })
        }
    }
}

// ---- file rows ------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCard(
    modifier: Modifier = Modifier,
    item: LibraryItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShare: () -> Unit,
    onRemoveFromList: () -> Unit,
    onDelete: () -> Unit,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "fileContainer",
    )
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val source = remember { MutableInteractionSource() }
    Surface(
        color = container,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(source)
            .clip(shape)
            .combinedClickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                MediaThumb(
                    model = item.thumbnail ?: item.uri.takeIf { item.kind == MediaKind.Video },
                    kind = item.kind,
                    modifier = Modifier.size(width = 88.dp, height = 64.dp),
                    shape = RoundedCornerShape(16.dp),
                )
                Icon(
                    if (item.kind == MediaKind.Audio) Icons.Rounded.MusicNote else Icons.Rounded.Videocam,
                    contentDescription = if (item.kind == MediaKind.Audio) "Audio" else "Video",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(Color(0x99000000), CircleShape)
                        .padding(4.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.platform.label, item.label.takeIf { it.isNotBlank() }, item.size.takeIf { it > 0 }?.let { formatBytes(it) })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    relativeTime(item.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            } else {
                FilledTonalIconButton(onClick = onClick) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${item.title}")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = { menu = false; onShare() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from list") },
                            leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                            onClick = { menu = false; onRemoveFromList() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete file") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, hasAny: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp).enterOnce("downloads-empty-$filtered"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (filtered) Icons.Rounded.Search else Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            if (filtered) "Nothing matches" else "No downloads yet",
            style = MaterialTheme.typography.titleLarge,
        )
        if (filtered && hasAny) TextButton(onClick = onClear) { Text("Clear filters") }
    }
}
