package com.arcadelabs.synapse.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import org.jetbrains.compose.resources.painterResource
import synapse.app.shared.generated.resources.Res
import synapse.app.shared.generated.resources.logo

/**
 * App mark on an M3 surface: surfaceContainerLowest fill with an
 * outlineVariant stroke, so it adapts to every theme in light and dark.
 *
 * [isDarkTheme] is kept for source compatibility; colors come from the theme.
 */
@Composable
fun SynapseLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    logoSize: Dp = size * 0.8f,
    cornerRadius: Dp = 10.dp,
    @Suppress("UNUSED_PARAMETER") isDarkTheme: Boolean = LocalSynapseDarkTheme.current
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = "Synapse logo",
                modifier = Modifier
                    .size(logoSize)
                    .clip(RoundedCornerShape(cornerRadius * 0.75f))
            )
        }
    }
}
