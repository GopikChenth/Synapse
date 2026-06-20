package com.arcadelabs.synapse.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FolderIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Folder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(10f, 4f)
        horizontalLineTo(4f)
        curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
        lineTo(2f, 18f)
        curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
        horizontalLineTo(20f)
        curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
        verticalLineTo(8f)
        curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
        horizontalLineTo(12f)
        lineTo(10f, 4f)
        close()
    }.build()
}

val DevicesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Devices",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 6f)
        horizontalLineTo(22f)
        verticalLineTo(4f)
        horizontalLineTo(4f)
        curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
        verticalLineTo(17f)
        horizontalLineTo(0f)
        verticalLineTo(20f)
        horizontalLineTo(14f)
        verticalLineTo(17f)
        horizontalLineTo(4f)
        verticalLineTo(6f)
        close()
        moveTo(23f, 8f)
        horizontalLineTo(17f)
        curveTo(16.45f, 8f, 16f, 8.45f, 16f, 9f)
        verticalLineTo(19f)
        curveTo(16f, 19.55f, 16.45f, 20f, 17f, 20f)
        horizontalLineTo(23f)
        curveTo(23.55f, 20f, 24f, 19.55f, 24f, 19f)
        verticalLineTo(9f)
        curveTo(24f, 8.45f, 23.55f, 8f, 23f, 8f)
        close()
        moveTo(22f, 17f)
        horizontalLineTo(18f)
        verticalLineTo(10f)
        horizontalLineTo(22f)
        verticalLineTo(17f)
        close()
    }.build()
}
