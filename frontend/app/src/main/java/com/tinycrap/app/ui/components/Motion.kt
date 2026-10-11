package com.tinycrap.app.ui.components

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** True when the user turned system animations off ("Remove animations" / animator scale 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun rememberEnter(): EnterTransition {
    val reduced = rememberReducedMotion()
    return if (reduced) EnterTransition.None
    else fadeIn(tween(200)) + expandVertically(tween(250))
}

@Composable
fun rememberExit(): ExitTransition {
    val reduced = rememberReducedMotion()
    return if (reduced) ExitTransition.None
    else fadeOut(tween(150)) + shrinkVertically(tween(200))
}
