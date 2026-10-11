package com.tinycrap.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Opens a downloaded file in the phone's own video or music player. */
fun openMedia(context: Context, uri: String, mime: String) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(uri), mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can play this file.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open the file. It may have been moved or deleted.", Toast.LENGTH_LONG).show()
    }
}

fun shareMedia(context: Context, uri: String, mime: String, title: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        .putExtra(Intent.EXTRA_TITLE, title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share the file.", Toast.LENGTH_LONG).show()
    }
}

/** "Just now", "5 minutes ago", "Yesterday", "3 days ago"… */
fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    return DateUtils.getRelativeTimeSpanString(
        timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

/** Returns a function that gives a light tick when haptics are enabled in Settings. */
@Composable
fun rememberHaptic(enabled: Boolean): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(enabled, haptic) {
        { if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}
