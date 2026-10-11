package com.tinycrap.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tinycrap.app.data.AudioInfo
import com.tinycrap.app.data.formatSize

/**
 * Audio, simplified: one big "Best Audio" button (original stream, no re-encoding) and, below it,
 * optional conversion. Source quality and converted output quality are kept visibly separate.
 */
@Composable
fun AudioTab(
    audio: AudioInfo,
    selectedFormat: String,
    selectedBitrate: Int?,
    busy: Boolean,
    onFormat: (String) -> Unit,
    onBitrate: (Int) -> Unit,
    onBestAudio: () -> Unit,
    onConvert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val best = audio.formats.firstOrNull { it.format == "best" }
    val converted = audio.formats.filter { it.format != "best" }
    val chosen = converted.firstOrNull { it.format == selectedFormat }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // One-click best audio --------------------------------------------------------------
        Button(
            onClick = onBestAudio,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Best audio", style = MaterialTheme.typography.titleSmall)
                best?.let {
                    Text(
                        "Original ${it.ext.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        if (converted.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                converted.forEach { f ->
                    FilterChip(
                        selected = f.format == selectedFormat,
                        onClick = { onFormat(f.format) },
                        enabled = !busy,
                        label = { Text(f.label) },
                    )
                }
            }

            if (chosen != null) {
                if (chosen.bitrates.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        chosen.bitrates.forEach { b ->
                            val size = formatSize(b.size, true)
                            FilterChip(
                                selected = b.value == selectedBitrate,
                                onClick = { onBitrate(b.value) },
                                enabled = !busy,
                                label = { Text(listOfNotNull("${b.value} kbps", size).joinToString(" · ")) },
                            )
                        }
                    }
                }
                val aboveSource = chosen.bitrates.firstOrNull { it.value == selectedBitrate }?.exceedsSource == true
                if (aboveSource) Text(
                    text = if (aboveSource) "Higher than the source quality" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (aboveSource) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onConvert,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download ${chosen.label}")
                }
            }
        }
    }
}
