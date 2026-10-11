package com.tinycrap.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tinycrap.app.data.MediaKind
import com.tinycrap.app.data.STAGE_LABELS

/**
 * A thumbnail: the remote picture when there is one, otherwise a frame of the saved video,
 * and a plain icon while it loads or when nothing is available.
 */
@Composable
fun MediaThumb(
    model: Any?,
    kind: MediaKind,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    description: String? = null,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (kind == MediaKind.Audio) Icons.Rounded.MusicNote else Icons.Rounded.Videocam,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        if (model != null) {
            AsyncImage(
                model = androidx.compose.runtime.remember(model) { ImageRequest.Builder(context).data(model).crossfade(true).build() },
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        trailing?.invoke()
    }
}

/** Six flat segments, one per stage: Preparing → Analyzing → Downloading → Processing → Finalizing → Ready. */
@Composable
fun StageSteps(current: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            STAGE_LABELS.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (index <= current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                )
            }
        }
    }
}
