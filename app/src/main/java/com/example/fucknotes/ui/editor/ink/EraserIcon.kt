package com.example.fucknotes.ui.editor.ink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A bevelled eraser with a baseline, distinct from delete and magic-wand actions. */
val EraserIcon: ImageVector by lazy {
    ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(14.6f, 3f); lineTo(22f, 10.4f); lineTo(11.4f, 21f)
            lineTo(6.6f, 21f); lineTo(1f, 15.4f); close()
            moveTo(14.6f, 5.8f); lineTo(3.8f, 15.4f); lineTo(7.4f, 19f)
            lineTo(10.6f, 19f); lineTo(19.2f, 10.4f); close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 10f); lineTo(9.4f, 8.6f); lineTo(16.8f, 16f)
            lineTo(15.4f, 17.4f); close()
            moveTo(14f, 19f); lineTo(23f, 19f); lineTo(23f, 21f); lineTo(14f, 21f); close()
        }
    }.build()
}
