package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Material Symbols Outlined: clinical_notes
 * Document with text lines and a person avatar.
 *
 * Source: https://fonts.google.com/icons?selected=Material+Symbols+Outlined:clinical_notes
 *
 * Bounding box: viewport 24x24
 */
val IcClinicalNotes: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcClinicalNotes",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            // Circle (person head)
            moveTo(17f, 16f)
            quadToRelative(-1.25f, 0f, -2.125f, -0.875f)
            reflectiveQuadTo(14f, 13f)
            reflectiveQuadToRelative(0.875f, -2.125f)
            reflectiveQuadTo(17f, 10f)
            reflectiveQuadToRelative(2.125f, 0.875f)
            reflectiveQuadTo(20f, 13f)
            reflectiveQuadToRelative(-0.875f, 2.125f)
            reflectiveQuadTo(17f, 16f)
            // Person body / bottom section
            moveToRelative(-6f, 7f)
            verticalLineToRelative(-2.9f)
            quadToRelative(0f, -0.525f, 0.25f, -0.987f)
            reflectiveQuadToRelative(0.7f, -0.738f)
            quadToRelative(0.8f, -0.475f, 1.688f, -0.788f)
            reflectiveQuadToRelative(1.812f, -0.462f)
            lineTo(17f, 19f)
            lineToRelative(1.55f, -1.875f)
            quadToRelative(0.925f, 0.15f, 1.8f, 0.463f)
            reflectiveQuadToRelative(1.675f, 0.787f)
            quadToRelative(0.45f, 0.275f, 0.713f, 0.738f)
            reflectiveQuadTo(23f, 20.1f)
            verticalLineTo(23f)
            close()
            // Document body with text lines
            moveToRelative(-2f, -2.9f)
            verticalLineToRelative(0.9f)
            horizontalLineTo(5f)
            quadToRelative(-0.825f, 0f, -1.412f, -0.587f)
            reflectiveQuadTo(3f, 19f)
            verticalLineTo(5f)
            quadToRelative(0f, -0.825f, 0.588f, -1.412f)
            reflectiveQuadTo(5f, 3f)
            horizontalLineToRelative(14f)
            quadToRelative(0.825f, 0f, 1.413f, 0.588f)
            reflectiveQuadTo(21f, 5f)
            verticalLineToRelative(5f)
            quadToRelative(-0.775f, -0.975f, -1.75f, -1.487f)
            reflectiveQuadTo(17f, 8f)
            verticalLineTo(7f)
            horizontalLineTo(7f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(7f)
            quadToRelative(-0.5f, 0.4f, -0.9f, 0.9f)
            reflectiveQuadToRelative(-0.675f, 1.1f)
            horizontalLineTo(7f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(5f)
            quadToRelative(0f, 0.525f, 0.113f, 1.025f)
            reflectiveQuadToRelative(0.312f, 0.975f)
            horizontalLineTo(7f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(3.45f)
            quadToRelative(-0.675f, 0.575f, -1.062f, 1.388f)
            reflectiveQuadTo(9f, 20.1f)
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcClinicalNotesPreview() {
    Icon(
        imageVector = IcClinicalNotes,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
