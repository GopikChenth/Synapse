package com.arcadelabs.synapse.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import synapse.app.shared.generated.resources.Res
import synapse.app.shared.generated.resources.logo

@Composable
fun SynapseLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    logoSize: Dp = size * 0.8f,
    cornerRadius: Dp = 10.dp,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val backgroundColor = if (isDarkTheme) Color.Black else Color.White
    val borderColor = if (isDarkTheme) Color(0xFF2B2928) else Color(0xFFE5E5E5)
    val shape = RoundedCornerShape(cornerRadius)
    val imageShape = RoundedCornerShape((cornerRadius.value * 0.75f).dp)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = "Synapse Logo",
            modifier = Modifier
                .size(logoSize)
                .clip(imageShape)
        )
    }
}
