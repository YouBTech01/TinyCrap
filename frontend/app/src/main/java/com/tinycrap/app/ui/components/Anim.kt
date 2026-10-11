package com.tinycrap.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** One set of motion values for the whole app, so every screen moves the same way. */
object Motion {
    /** Things that move or resize: a soft spring with a tiny overshoot. */
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = 420f)

    /** Colour and opacity changes: short and calm. */
    fun <T> effects(millis: Int = 220): TweenSpec<T> = tween(millis, easing = FastOutSlowInEasing)
}

/** Presses shrink the element slightly and spring back. Only the draw layer changes, so nothing recomposes while animating. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Fades and slides an item up the first time it appears, with a small stagger by [index].
 * It remembers that it already played, so scrolling a list back and forth stays still.
 */
@Composable
fun Modifier.enterOnce(key: Any, index: Int = 0, offsetY: Dp = 16.dp): Modifier {
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!played) {
            delay(index.coerceIn(0, 8) * 45L)
            progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 320f))
            played = true
        }
    }
    val offsetPx = with(LocalDensity.current) { offsetY.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * offsetPx
    }
}
