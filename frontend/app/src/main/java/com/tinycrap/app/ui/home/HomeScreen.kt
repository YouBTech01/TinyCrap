package com.tinycrap.app.ui.home

import com.tinycrap.app.ui.components.pressScale
import com.tinycrap.app.ui.components.enterOnce
import com.tinycrap.app.ui.components.Motion
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.AnimatedContent
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinycrap.app.data.LibraryItem
import com.tinycrap.app.data.MediaInfo
import com.tinycrap.app.data.Platform
import com.tinycrap.app.ui.AnalyzeState
import com.tinycrap.app.ui.MainViewModel
import com.tinycrap.app.ui.ResultTab
import com.tinycrap.app.ui.UiState
import com.tinycrap.app.ui.components.AudioTab
import com.tinycrap.app.ui.components.ErrorBanner
import com.tinycrap.app.ui.components.MediaThumb
import com.tinycrap.app.ui.components.SectionLabel
import com.tinycrap.app.ui.components.TaskCard
import com.tinycrap.app.ui.components.VideoTab
import com.tinycrap.app.ui.util.openMedia

@Composable
fun HomeScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onSeeDownloads: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lastTask by vm.lastTask.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Box(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Tiny Crap") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "hero") {
                    LinkCard(
                        modifier = Modifier.enterOnce("home-hero", 0),
                        state = state,
                        onInput = vm::onInputChange,
                        onSubmit = vm::submit,
                        onPasted = vm::onPasted,
                        onClear = vm::clearInput,
                        onPlatform = vm::onPlatformSelected,
                    )
                }

                item(key = "loading") {
                    AnimatedVisibility(
                        visible = state.analyze is AnalyzeState.Loading,
                        enter = fadeIn(Motion.effects()) + expandVertically(Motion.spatial()),
                        exit = fadeOut(Motion.effects(150)) + shrinkVertically(Motion.spatial()),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 4.dp)
                        }
                    }
                }

                (state.analyze as? AnalyzeState.Failed)?.let { failed ->
                    item(key = "error") { ErrorBanner(failed.message) }
                }

                (state.analyze as? AnalyzeState.Ready)?.let { ready ->
                    item(key = "media-${ready.media.url}") {
                        MediaCard(ready.media, Modifier.animateItem().enterOnce(ready.media.url, 0))
                    }
                    item(key = "options") {
                        OptionsCard(ready.media, state, vm, Modifier.animateItem().enterOnce("opt-" + ready.media.url, 1))
                    }
                }

                lastTask?.let { task ->
                    item(key = "task-${task.id}") {
                        TaskCard(
                            modifier = Modifier.animateItem().enterOnce(task.id, 0),
                            task = task,
                            onCancel = { vm.cancelTask(task.id) },
                            onRetry = { vm.retryTask(task.id) },
                            onDismiss = { vm.dismissTask(task.id) },
                        )
                    }
                }

                if (state.analyze is AnalyzeState.Idle || state.analyze is AnalyzeState.Failed) {
                    if (recent.isNotEmpty()) {
                        item(key = "recent-title") {
                            SectionLabel("Recent") {
                                androidx.compose.material3.TextButton(onClick = onSeeDownloads) { Text("See all") }
                            }
                        }
                        items(recent.size, key = { "recent-${recent[it].id}" }) { index ->
                            RecentRow(recent[index], Modifier.animateItem().enterOnce("recent-" + recent[index].id, index))
                        }
                    }
                }
            }
        }
    }
}

// ---- the link box ------------------------------------------------------------------------------

@Composable
private fun LinkCard(
    state: UiState,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onPasted: (String) -> Unit,
    onClear: () -> Unit,
    onPlatform: (Platform) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val loading = state.analyze is AnalyzeState.Loading
    val submit = { focus.clearFocus(); onSubmit() }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = state.input,
                onValueChange = onInput,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste a link") },
                singleLine = true,
                enabled = !loading,
                shape = CircleShape,
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                trailingIcon = {
                    if (state.input.isNotEmpty()) {
                        IconButton(onClick = onClear, enabled = !loading) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear link")
                        }
                    } else {
                        FilledTonalIconButton(onClick = { readClipboard(context)?.let(onPasted) }) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste link from clipboard")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )

            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Platform.entries.forEach { platform ->
                    FilterChip(
                        selected = platform == state.platform,
                        onClick = { onPlatform(platform) },
                        label = { Text(platform.label, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Button(
                onClick = submit,
                enabled = state.input.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (loading) "Reading link…" else "Get media", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun readClipboard(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
}

// ---- analysed media -------------------------------------------------------------------------------

@Composable
private fun MediaCard(media: MediaInfo, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box {
                MediaThumb(
                    model = media.thumbnail,
                    kind = com.tinycrap.app.data.MediaKind.Video,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(18.dp),
                    description = "Thumbnail: ${media.title}",
                )
                if (media.durationText != null) {
                    Surface(
                        color = Color(0xCC000000),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    ) {
                        Text(
                            media.durationText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                ) {
                    Text(
                        media.platform.label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(media.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                media.uploader?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun OptionsCard(media: MediaInfo, state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth().animateContentSize(Motion.spatial()),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val tabs = listOf(ResultTab.Video to "Video", ResultTab.Audio to "Audio")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, (tab, label) ->
                    val enabled = if (tab == ResultTab.Video) media.video.available else media.audio.available
                    SegmentedButton(
                        selected = state.tab == tab,
                        onClick = { vm.selectTab(tab) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = state.tab == tab) {
                                Icon(
                                    if (tab == ResultTab.Video) Icons.Rounded.Videocam else Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            }
                        },
                    ) { Text(label) }
                }
            }
            AnimatedContent(
                targetState = state.tab,
                transitionSpec = {
                    val dir = if (targetState == ResultTab.Audio) 1 else -1
                    (fadeIn(Motion.effects(220)) + slideInHorizontally(Motion.spatial()) { it / 8 * dir }) togetherWith
                        (fadeOut(Motion.effects(120)) + slideOutHorizontally(Motion.spatial()) { -it / 8 * dir })
                },
                label = "optionsTab",
            ) { tab ->
                when (tab) {
                    ResultTab.Video ->
                        if (media.video.available) {
                            VideoTab(
                                video = media.video,
                                qualityKey = state.qualityKey,
                                container = state.container,
                                busy = false,
                                onQuality = vm::selectQuality,
                                onContainer = vm::selectContainer,
                                onDownload = vm::downloadVideo,
                            )
                        } else {
                            Text("No video formats are available for this link.", style = MaterialTheme.typography.bodyLarge)
                        }
                    ResultTab.Audio ->
                        if (media.audio.available) {
                            AudioTab(
                                audio = media.audio,
                                selectedFormat = state.audioFormat,
                                selectedBitrate = state.audioBitrate,
                                busy = false,
                                onFormat = vm::selectAudioFormat,
                                onBitrate = vm::selectAudioBitrate,
                                onBestAudio = vm::downloadBestAudio,
                                onConvert = vm::downloadConvertedAudio,
                            )
                        } else {
                            Text("No audio is available for this link.", style = MaterialTheme.typography.bodyLarge)
                        }
                }
            }
        }
    }
}

// ---- empty state -------------------------------------------------------------------------------------

@Composable
private fun RecentRow(item: LibraryItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .pressScale(source)
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current) { openMedia(context, item.uri, item.mime) },
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaThumb(item.thumbnail ?: item.uri.takeIf { item.kind == com.tinycrap.app.data.MediaKind.Video }, item.kind, Modifier.size(52.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(item.platform.label, item.label).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(onClick = { openMedia(context, item.uri, item.mime) }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${item.title}")
            }
        }
    }
}
