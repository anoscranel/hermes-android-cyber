package com.m57.hermescontrol.ui.common

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.theme.IconDefaultFill

/**
 * Temporary-chat glyph (batch-7 W7): a ChatGPT-style speech bubble drawn as
 * DASHED rounded-rect strokes with a small tail at the lower left. Material
 * ships no dashed bubble, so it is declared here in house vector style —
 * 24dp viewport, stroke-only path, IconDefaultFill so `Icon(tint = ...)`
 * recolors it.
 */
val TempChatIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "TempChat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(PATH),
            pathFillType = PathFillType.NonZero,
            name = "BubblePath",
            fill = null,
            fillAlpha = 1f,
            stroke = SolidColor(IconDefaultFill),
            strokeAlpha = 1f,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 4f,
            trimPathStart = 0f,
            trimPathEnd = 1f,
            trimPathOffset = 0f,
        ).build()
}

// Six dash segments trace a rounded bubble ([4,4]-[20,16], corner radius ~3)
// with a tail dropping from the bottom edge to (8,20) and back:
// 1. TL corner + first half of the top edge
// 2. second half of the top edge + TR corner + upper right edge
// 3. lower right edge + BR corner + first part of the bottom edge
// 4. rest of the bottom edge out into the tail apex
// 5. tail apex back up + BL corner + lower left edge
// 6. mid left edge — the remaining dashes are the gaps by construction.
private const val PATH =
    "M4,9 L4,7 Q4,4 7,4 L10,4 M14,4 L17,4 Q20,4 20,7 L20,9.5 M20,12.5 L20,13 Q20,16 17,16 L14,16 " +
        "M11.5,16 L8,20 M8,20 L8,16 L7,16 Q4,16 4,13 M4,12 L4,10"
