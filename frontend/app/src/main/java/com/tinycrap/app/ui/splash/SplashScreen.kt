package com.tinycrap.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DURATION_MS = 2600

private val EaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EaseInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val EaseIn = CubicBezierEasing(0.6f, 0f, 0.9f, 0.4f)

/** 0..1 progress of [t] between [from] and [to] (clamped), eased with [easing]. */
private fun seg(t: Float, from: Float, to: Float, easing: androidx.compose.animation.core.Easing = LinearEasing): Float =
    easing.transform(((t - from) / (to - from)).coerceIn(0f, 1f))

/** Springy overshoot used for the crab's entrance. */
private fun overshoot(x: Float): Float {
    val t = x - 1f
    val k = 2.2f
    return t * t * ((k + 1f) * t + k) + 1f
}

private class SocialChip(val icon: ImageVector, val color: Color, val startAngle: Float)

/**
 * Opening animation: the crab pops in, three generic "social media" bubbles (video, photo, people)
 * swing around it, then get gobbled up as the crab grabs the download. About 2.6 seconds; tap to skip.
 * These are plain Material icons, not the platforms' own logos.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color(0xFF171313) else Color(0xFFF7F7F7)
    val textColor = if (dark) Color(0xFFF7F7F7) else Color(0xFF3F4C5B)
    val finished by rememberUpdatedState(onFinished)

    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
        finished()
    }

    val chips = remember {
        listOf(
            SocialChip(Icons.Rounded.PlayArrow, Color(0xFFEA4E56), -90f),
            SocialChip(Icons.Rounded.PhotoCamera, Color(0xFFB5389A), 30f),
            SocialChip(Icons.Rounded.ThumbUp, Color(0xFF3F6FD8), 150f),
        )
    }
    val art = remember { CrabPaths() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finished() },
        contentAlignment = Alignment.Center,
    ) {
        // Crab ---------------------------------------------------------------------------------
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    val v = t.value
                    val enter = overshoot(seg(v, 0f, 0.30f))
                    val gulp = 1f + 0.10f * pulse(seg(v, 0.62f, 0.80f))
                    val s = (0.55f + 0.45f * enter) * gulp
                    scaleX = s
                    scaleY = s
                    alpha = seg(v, 0f, 0.12f)
                    translationY = -28.dp.toPx() * seg(v, 0.55f, 0.95f, EaseInOut)
                },
        ) {
            val v = t.value
            val wave = sin(seg(v, 0.2f, 0.9f) * 2f * PI.toFloat() * 2.5f) * 9f * (1f - seg(v, 0.8f, 0.95f))
            val sc = size.width / 512f
            scale(sc, pivot = Offset.Zero) {
                art.drawCrab(this, waveLeft = -wave, waveRight = wave)
            }
        }

        // Social bubbles ------------------------------------------------------------------------
        chips.forEachIndexed { index, chip ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        val v = t.value
                        val delay = index * 0.04f
                        val fly = seg(v, 0.10f + delay, 0.50f, EaseOut)        // fly in
                        val swallow = seg(v, 0.60f + delay, 0.80f, EaseIn)     // fly into the crab
                        val orbitProgress = seg(v, 0.10f + delay, 0.66f, EaseInOut)
                        val radius = (1f - fly) * 420.dp.toPx() + 120.dp.toPx() * fly * (1f - swallow)
                        val angle = Math.toRadians((chip.startAngle + 300f * orbitProgress).toDouble())
                        translationX = (cos(angle) * radius).toFloat()
                        translationY = (sin(angle) * radius).toFloat() - 28.dp.toPx() * seg(v, 0.55f, 0.95f, EaseInOut)
                        val s = fly * (1f - swallow * 0.85f)
                        scaleX = s
                        scaleY = s
                        alpha = if (swallow > 0.97f) 0f else 1f
                        rotationZ = 360f * (1f - fly)
                    }
                    .clip(CircleShape)
                    .background(chip.color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(chip.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        // Name ---------------------------------------------------------------------------------
        Text(
            text = "Tiny Crap",
            color = textColor,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val v = t.value
                    val a = seg(v, 0.74f, 0.90f, EaseOut)
                    alpha = a * (1f - seg(v, 0.94f, 1f))
                    translationY = 150.dp.toPx() + (1f - a) * 24.dp.toPx()
                },
        )
    }
}

/** 0 → 1 → 0 bump. */
private fun pulse(x: Float): Float = sin(x * PI.toFloat())

/** The crab's shapes as ready-to-draw paths. */
private class CrabPaths {
    private fun build(parts: List<CrabArt.Part>) = parts.map { Color(it.color) to PathParser().parsePathString(it.d).toPath() }

    val legs = build(CrabArt.legs)
    val leftClaw = build(CrabArt.leftClaw)
    val rightClaw = build(CrabArt.rightClaw)
    val stalks = build(CrabArt.eyeStalks)
    val shell: Pair<Color, Path> = Color(CrabArt.shell.color) to PathParser().parsePathString(CrabArt.shell.d).toPath()

    /** Draws in the SVG's 512 x 512 space; the claws swing around their shoulders. */
    fun drawCrab(scope: DrawScope, waveLeft: Float, waveRight: Float) = with(scope) {
        legs.forEach { (c, p) -> drawPath(p, c) }
        rotate(waveLeft, pivot = Offset(190f, 240f)) { leftClaw.forEach { (c, p) -> drawPath(p, c) } }
        rotate(waveRight, pivot = Offset(322f, 240f)) { rightClaw.forEach { (c, p) -> drawPath(p, c) } }
        stalks.forEach { (c, p) -> drawPath(p, c) }
        CrabArt.eyeDots.forEachIndexed { i, d ->
            drawCircle(Color(CrabArt.eyeDotColors[i]), radius = d[2], center = Offset(d[0], d[1]))
        }
        drawPath(shell.second, shell.first)
    }
}
