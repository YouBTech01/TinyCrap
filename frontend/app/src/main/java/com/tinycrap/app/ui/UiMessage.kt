package com.tinycrap.app.ui

/** A short message for the snackbar; [viewDownloads] adds a "View" action that jumps to the Downloaded tab. */
data class UiMessage(val text: String, val viewDownloads: Boolean = false)
