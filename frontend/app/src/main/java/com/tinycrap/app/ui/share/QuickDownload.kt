package com.tinycrap.app.ui.share

import com.tinycrap.app.ui.components.Motion
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinycrap.app.data.MediaInfo
import com.tinycrap.app.data.MediaKind
import com.tinycrap.app.ui.AnalyzeState
import com.tinycrap.app.ui.MainViewModel
import com.tinycrap.app.ui.ResultTab
import com.tinycrap.app.ui.components.AudioTab
import com.tinycrap.app.ui.components.ErrorBanner
import com.tinycrap.app.ui.components.MediaThumb
import com.tinycrap.app.ui.components.TaskCard
import com.tinycrap.app.ui.components.VideoTab

/** The sheet shown over another app: every quality and format, then download. */
@Composable
fun QuickDownloadContent(vm: MainViewModel, onClose: () -> Unit, onOpenApp: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val task by vm.lastTask.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .navigationBarsPadding()
            .animateContentSize(Motion.spatial()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val analysis = state.analyze) {
            is AnalyzeState.Idle, is AnalyzeState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
            is AnalyzeState.Failed -> {
                ErrorBanner(analysis.message)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = vm::submit, modifier = Modifier.weight(1f)) { Text("Try again") }
                    TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Close") }
                }
            }
            is AnalyzeState.Ready -> {
                CompactMedia(analysis.media)
                Options(analysis.media, vm, state.tab, state.qualityKey, state.container, state.audioFormat, state.audioBitrate)
                task?.let {
                    TaskCard(
                        task = it,
                        onCancel = { vm.cancelTask(it.id) },
                        onRetry = { vm.retryTask(it.id) },
                        onDismiss = { vm.dismissTask(it.id) },
                    )
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onOpenApp) { Text("Open Tiny Crap") }
                    TextButton(onClick = onClose) { Text(if (task != null) "Done" else "Close") }
                }
            }
        }
    }
}

@Composable
private fun CompactMedia(media: MediaInfo) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        MediaThumb(
            model = media.thumbnail,
            kind = MediaKind.Video,
            modifier = Modifier.size(width = 96.dp, height = 60.dp),
            shape = RoundedCornerShape(14.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(media.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(media.platform.label, media.uploader, media.durationText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Options(
    media: MediaInfo, vm: MainViewModel, tab: ResultTab,
    qualityKey: String?, container: String?, audioFormat: String, audioBitrate: Int?,
) {
    val tabs = listOf(ResultTab.Video to "Video", ResultTab.Audio to "Audio")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, (t, label) ->
            SegmentedButton(
                selected = tab == t,
                onClick = { vm.selectTab(t) },
                enabled = if (t == ResultTab.Video) media.video.available else media.audio.available,
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = tab == t) {
                        Icon(
                            if (t == ResultTab.Video) Icons.Rounded.Videocam else Icons.Rounded.MusicNote,
                            contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    }
                },
            ) { Text(label) }
        }
    }
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            val dir = if (targetState == ResultTab.Audio) 1 else -1
            (fadeIn(Motion.effects(220)) + slideInHorizontally(Motion.spatial()) { it / 8 * dir }) togetherWith
                (fadeOut(Motion.effects(120)) + slideOutHorizontally(Motion.spatial()) { -it / 8 * dir })
        },
        label = "shareTab",
    ) { shown ->
        when (shown) {
            ResultTab.Video ->
                if (media.video.available) {
                    VideoTab(
                        video = media.video, qualityKey = qualityKey, container = container, busy = false,
                        onQuality = vm::selectQuality, onContainer = vm::selectContainer, onDownload = vm::downloadVideo,
                    )
                } else {
                    Text("No video formats are available for this link.", style = MaterialTheme.typography.bodyMedium)
                }
            ResultTab.Audio ->
                if (media.audio.available) {
                    AudioTab(
                        audio = media.audio, selectedFormat = audioFormat, selectedBitrate = audioBitrate, busy = false,
                        onFormat = vm::selectAudioFormat, onBitrate = vm::selectAudioBitrate,
                        onBestAudio = vm::downloadBestAudio, onConvert = vm::downloadConvertedAudio,
                    )
                } else {
                    Text("No audio is available for this link.", style = MaterialTheme.typography.bodyMedium)
                }
        }
    }
    }
