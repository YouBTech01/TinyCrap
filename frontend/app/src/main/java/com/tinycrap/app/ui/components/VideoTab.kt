package com.tinycrap.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tinycrap.app.data.VideoInfo
import com.tinycrap.app.data.VideoQuality
import com.tinycrap.app.data.VideoVariant
import com.tinycrap.app.data.formatSize

/** Lists only the qualities that really exist for this media, plus the output container choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTab(
    video: VideoInfo,
    qualityKey: String?,
    container: String?,
    busy: Boolean,
    onQuality: (String) -> Unit,
    onContainer: (String) -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = video.qualities.firstOrNull { it.key == qualityKey } ?: video.qualities.firstOrNull()
    val variant = selected?.variants?.firstOrNull { it.container == container } ?: selected?.variants?.firstOrNull()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            video.qualities.forEach { q ->
                val v = q.variants.firstOrNull { it.container == container } ?: q.variants.first()
                QualityRow(
                    quality = q,
                    variant = v,
                    selected = q.key == selected?.key,
                    onClick = { onQuality(q.key) },
                )
            }
        }

        if (selected != null && selected.variants.isNotEmpty()) {
            val options = selected.variants.map { it.container }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, c ->
                    SegmentedButton(
                        selected = c == variant?.container,
                        onClick = { onContainer(c) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) {
                        Text(c.uppercase())
                    }
                }
            }
        }

        Button(
            onClick = onDownload,
            enabled = !busy && variant != null && !variant.exceedsLimit,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download")
        }
    }
}

@Composable
private fun QualityRow(
    quality: VideoQuality,
    variant: VideoVariant,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tooLarge = variant.exceedsLimit
    val details = listOfNotNull(
        variant.fps?.let { "$it fps" },
        variant.codec,
        variant.container.uppercase(),
        if (variant.hdr) "HDR" else null,
    ).joinToString(" · ")
    val size = formatSize(variant.size, variant.sizeEstimated)
    val rowColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        Motion.effects(180), label = "qualityRow",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowColor)
            .selectable(selected = selected, enabled = !tooLarge, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = !tooLarge)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(quality.label, style = MaterialTheme.typography.titleSmall)
                if (quality.isBest) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            "Best",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (tooLarge) {
            Text("Over limit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        } else if (size != null) {
            Text(size, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
