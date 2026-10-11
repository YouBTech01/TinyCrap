package com.tinycrap.app.ui.components

import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tinycrap.app.data.DownloadTask
import com.tinycrap.app.data.STAGE_LABELS
import com.tinycrap.app.data.TaskState
import com.tinycrap.app.data.formatBytes
import com.tinycrap.app.data.formatEta
import com.tinycrap.app.data.formatSpeed
import com.tinycrap.app.ui.util.openMedia
import com.tinycrap.app.ui.util.shareMedia

/** One download in the queue: waiting, running, failed, or finished (Home keeps the finished card for a moment). */
@Composable
fun TaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = when (task.state) {
        TaskState.Failed -> MaterialTheme.colorScheme.errorContainer
        TaskState.Done -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val container by animateColorAsState(target, Motion.effects(320), label = "taskContainer")
    // 0 = queued/running, 1 = failed, 2 = done: the body cross-fades and the card resizes with a spring.
    val kind = when (task.state) {
        TaskState.Failed -> 1
        TaskState.Done -> 2
        else -> 0
    }
    Surface(color = container, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp).animateContentSize(Motion.spatial()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(task)
            AnimatedContent(
                targetState = kind,
                transitionSpec = { fadeIn(Motion.effects(220)) togetherWith fadeOut(Motion.effects(120)) },
                label = "taskBody",
            ) { k ->
                when (k) {
                    1 -> FailedBody(task, onRetry, onDismiss)
                    2 -> DoneBody(task, onDismiss)
                    else -> RunningBody(task, onCancel)
                }
            }
        }
    }
}

@Composable
private fun Header(task: DownloadTask) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        MediaThumb(
            model = task.request.thumbnail,
            kind = task.request.kind,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                task.request.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(task.request.platform.label, task.request.label).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RunningBody(task: DownloadTask, onCancel: () -> Unit) {
    val stage = when (task.state) {
        TaskState.Queued -> "Waiting in queue"
        TaskState.Saving -> "Saving to your phone"
        else -> if (task.queuePosition > 0) "In queue · #${task.queuePosition}" else STAGE_LABELS.getOrElse(task.stageIndex) { "Working" }
    }
    val detail = listOfNotNull(
        task.speed?.let { formatSpeed(it) },
        task.eta?.let { formatEta(it) },
    ).joinToString(" · ")
    // Read inside the progress lambda, so the bar animates in the draw phase without recomposing the card every frame.
    val progress = animateFloatAsState((task.percent / 100f).coerceIn(0f, 1f), tween(450, easing = LinearOutSlowInEasing), label = "progress")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stage, style = MaterialTheme.typography.titleSmall)
            if (!task.indeterminate) Text("${task.percent.toInt()}%", style = MaterialTheme.typography.titleSmall)
        }
        if (task.indeterminate) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = StrokeCap.Round)
        } else {
            LinearProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = StrokeCap.Round, gapSize = 3.dp, drawStopIndicator = {})
        }
        if (task.state != TaskState.Queued && task.queuePosition == 0) StageSteps(current = task.stageIndex)
        if (detail.isNotEmpty()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Cancel")
        }
    }
}

@Composable
private fun FailedBody(task: DownloadTask, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            task.error ?: "The download failed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onRetry, modifier = Modifier.weight(1f).height(44.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try again")
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp)) { Text("Remove") }
        }
    }
}

@Composable
private fun DoneBody(task: DownloadTask, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val saved = task.saved
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                listOfNotNull(
                    "Saved",
                    saved?.size?.takeIf { it > 0 }?.let { formatBytes(it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (saved != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { openMedia(context, saved.uri, saved.mime) },
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                FilledTonalButton(
                    onClick = { shareMedia(context, saved.uri, saved.mime, saved.title) },
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Dismiss") }
    }
}
