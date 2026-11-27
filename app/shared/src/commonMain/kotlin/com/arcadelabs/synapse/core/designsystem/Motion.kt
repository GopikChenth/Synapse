package com.arcadelabs.synapse.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Material 3 motion tokens (easing + duration) used across Synapse.
 * https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
 */
object SynapseMotion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val DurationShort4 = 200
    const val DurationMedium2 = 300
    const val DurationMedium4 = 400
    const val DurationLong2 = 500

    /** Full-screen dialog / page entering: rises slightly and fades in. */
    val fullScreenEnter: EnterTransition =
        slideInVertically(tween(DurationMedium4, easing = EmphasizedDecelerate)) { it / 10 } +
            fadeIn(tween(DurationMedium2, easing = EmphasizedDecelerate))

    /** Full-screen dialog / page leaving: drops slightly and fades out. */
    val fullScreenExit: ExitTransition =
        slideOutVertically(tween(DurationShort4, easing = EmphasizedAccelerate)) { it / 10 } +
            fadeOut(tween(DurationShort4, easing = EmphasizedAccelerate))
}

/**
 * An M3 full-screen dialog container: a `surface` sheet over the whole window,
 * animated with the emphasized enter/exit motion.
 */
@Composable
fun FullScreenOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = SynapseMotion.fullScreenEnter,
        exit = SynapseMotion.fullScreenExit,
        modifier = modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            content()
        }
    }
}
