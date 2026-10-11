package com.tinycrap.app.ui.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tinycrap.app.data.RequiredUpdate

/**
 * Full-screen wall shown while the server requires a newer version. There is no close button and the back
 * button does nothing. It goes away only once the newer APK is installed.
 */
@Composable
fun UpdateRequiredScreen(update: RequiredUpdate, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BackHandler(enabled = true) { /* cannot be skipped */ }
    Surface(
        modifier = modifier.fillMaxSize().pointerInput(Unit) { /* swallow every touch behind the screen */ },
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(32.dp), contentAlignment = Alignment.Center) {
            UpdateRequiredContent(update) { openLink(context, update.link) }
        }
    }
}

/** The same message without a wall around it (used inside the share sheet). */
@Composable
fun UpdateRequiredContent(update: RequiredUpdate, onUpdate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
        }
        Text(update.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (update.versionName.isNotBlank()) {
            Text("Version ${update.versionName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) { Text("Update") }
    }
}

fun openLink(context: android.content.Context, link: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser installed: nothing else to do; the wall stays up.
    }
}
