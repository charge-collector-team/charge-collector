package kr.chargebadge

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/** Fast start, long soft landing. */
val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Click without the ripple: the element sinks (at most 16dp, 6%) and dims, then springs back.
 * A quick tap still plays the whole dip.
 */
fun Modifier.press(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val amount = remember { Animatable(0f) }
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> launch { amount.animateTo(1f, tween(90)) }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                    amount.animateTo(1f, tween(((1f - amount.value) * 90).toInt()))
                    amount.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f))
                }
            }
        }
    }
    graphicsLayer {
        val k = min(0.06f, 16.dp.toPx() / maxOf(size.width, size.height, 1f))
        val s = 1f - amount.value * k
        scaleX = s
        scaleY = s
        alpha = 1f - 0.15f * amount.value.coerceIn(0f, 1f)
    }.clickable(source, null, enabled = enabled, role = role, onClick = onClick)
}

/** Rises [dy] and fades in once, [delayMillis] after it first appears. */
fun Modifier.enter(delayMillis: Int, dy: Dp = 28.dp): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        p.animateTo(1f, tween(520, easing = Emphasized))
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * dy.toPx()
    }
}

/**
 * A light band across the medal disc, which sits at the bottom of a [Medal] box (the ring tab is above it).
 * [progress] runs from about -0.2 to 1.2. Clipped to the disc, not to an offscreen layer, so the medal's shadow stays whole.
 */
fun Modifier.shine(progress: () -> Float): Modifier = drawWithContent {
    drawContent()
    val p = progress()
    if (p <= -0.2f || p >= 1.2f) return@drawWithContent
    val r = size.width / 2
    val disc = Path().apply { addOval(Rect(Offset(r, size.height - r), r)) }
    val x = size.width * p
    val band = size.width * 0.28f
    clipPath(disc) {
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.45f), 1f to Color.Transparent,
                start = Offset(x - band, size.height - 2 * r), end = Offset(x + band, size.height),
            ),
        )
    }
}

/** Panels swapped in place: the old one fades out fast, the new one fades and settles in. */
fun fadeThrough(): ContentTransform =
    (fadeIn(tween(240, delayMillis = 70)) + scaleIn(tween(360, delayMillis = 70, easing = Emphasized), initialScale = 0.97f)) togetherWith
        fadeOut(tween(110))
