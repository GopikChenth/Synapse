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

val QrCodeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "QrCode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(2f, 2f)
        verticalLineTo(8f)
        horizontalLineTo(8f)
        verticalLineTo(2f)
        close()
        moveTo(3.5f, 3.5f)
        horizontalLineTo(6.5f)
        verticalLineTo(6.5f)
        horizontalLineTo(3.5f)
        close()

        moveTo(16f, 2f)
        verticalLineTo(8f)
        horizontalLineTo(22f)
        verticalLineTo(2f)
        close()
        moveTo(17.5f, 3.5f)
        horizontalLineTo(20.5f)
        verticalLineTo(6.5f)
        horizontalLineTo(17.5f)
        close()

        moveTo(2f, 16f)
        verticalLineTo(22f)
        horizontalLineTo(8f)
        verticalLineTo(16f)
        close()
        moveTo(3.5f, 17.5f)
        horizontalLineTo(6.5f)
        verticalLineTo(20.5f)
        horizontalLineTo(3.5f)
        close()

        moveTo(12f, 12f)
        horizontalLineTo(14f)
        verticalLineTo(14f)
        horizontalLineTo(12f)
        close()
        
        moveTo(16f, 16f)
        horizontalLineTo(18f)
        verticalLineTo(18f)
        horizontalLineTo(16f)
        close()

        moveTo(20f, 20f)
        horizontalLineTo(22f)
        verticalLineTo(22f)
        horizontalLineTo(20f)
        close()

        moveTo(10f, 18f)
        horizontalLineTo(12f)
        verticalLineTo(20f)
        horizontalLineTo(10f)
        close()

        moveTo(18f, 10f)
        horizontalLineTo(20f)
        verticalLineTo(12f)
        horizontalLineTo(18f)
        close()
    }.build()
}

val ImportExportIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ImportExport",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(9f, 6f)
        lineTo(5f, 10f)
        horizontalLineTo(8f)
        verticalLineTo(18f)
        horizontalLineTo(10f)
        verticalLineTo(8f)
        horizontalLineTo(13f)
        close()
        
        moveTo(15f, 16f)
        horizontalLineTo(11f)
        lineTo(15f, 20f)
        lineTo(19f, 16f)
        horizontalLineTo(16f)
        verticalLineTo(6f)
        horizontalLineTo(14f)
        verticalLineTo(16f)
        close()
    }.build()
}

val ExitIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Exit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(10.09f, 15.59f)
        lineTo(11.5f, 17f)
        lineTo(16.5f, 12f)
        lineTo(11.5f, 7f)
        lineTo(10.09f, 8.41f)
        lineTo(12.67f, 11f)
        horizontalLineTo(3f)
        verticalLineTo(13f)
        horizontalLineTo(12.67f)
        close()
        
        moveTo(19f, 3f)
        horizontalLineTo(5f)
        curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
        verticalLineTo(9f)
        horizontalLineTo(5f)
        verticalLineTo(5f)
        horizontalLineTo(19f)
        verticalLineTo(19f)
        horizontalLineTo(5f)
        verticalLineTo(15f)
        horizontalLineTo(3f)
        verticalLineTo(19f)
        curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
        horizontalLineTo(19f)
        curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
        verticalLineTo(5f)
        curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
        close()
    }.build()
}

val WebIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Web",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
        close()
        
        moveTo(12f, 4f)
        curveTo(13.82f, 6.6f, 14.88f, 9.45f, 15f, 12f)
        curveTo(14.88f, 14.55f, 13.82f, 17.4f, 12f, 20f)
        curveTo(10.18f, 17.4f, 9.12f, 14.55f, 9f, 12f)
        curveTo(9.12f, 9.45f, 10.18f, 6.6f, 12f, 4f)
        close()
        
        moveTo(4.07f, 13f)
        horizontalLineTo(8.03f)
        curveTo(8.17f, 15.48f, 8.76f, 17.73f, 9.63f, 19.53f)
        curveTo(6.83f, 18.75f, 4.76f, 16.18f, 4.07f, 13f)
        close()
        
        moveTo(4.07f, 11f)
        curveTo(4.76f, 7.82f, 6.83f, 5.25f, 9.63f, 4.47f)
        curveTo(8.76f, 6.27f, 8.17f, 8.52f, 8.03f, 11f)
        close()
        
        moveTo(14.37f, 4.47f)
        curveTo(15.24f, 6.27f, 15.83f, 8.52f, 15.97f, 11f)
        horizontalLineTo(19.93f)
        curveTo(19.24f, 7.82f, 17.17f, 5.25f, 14.37f, 4.47f)
        close()
        
        moveTo(14.37f, 19.53f)
        curveTo(17.17f, 18.75f, 19.24f, 16.18f, 19.93f, 13f)
        horizontalLineTo(15.97f)
        curveTo(15.83f, 15.48f, 15.24f, 17.73f, 14.37f, 19.53f)
        close()
    }.build()
}
