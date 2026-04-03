package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

val IcSetupWizard: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcSetupWizard",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(220f, 600f)
            verticalLineToRelative(-180f)
            horizontalLineToRelative(-60f)
            verticalLineToRelative(-60f)
            horizontalLineToRelative(120f)
            verticalLineToRelative(240f)
            horizontalLineToRelative(-60f)
            close()
            moveTo(360f, 600f)
            verticalLineToRelative(-100f)
            quadToRelative(0f, -17f, 11.5f, -28.5f)
            reflectiveQuadTo(400f, 460f)
            horizontalLineToRelative(80f)
            verticalLineToRelative(-40f)
            horizontalLineTo(360f)
            verticalLineToRelative(-60f)
            horizontalLineToRelative(140f)
            quadToRelative(17f, 0f, 28.5f, 11.5f)
            reflectiveQuadTo(540f, 400f)
            verticalLineToRelative(60f)
            quadToRelative(0f, 17f, -11.5f, 28.5f)
            reflectiveQuadTo(500f, 500f)
            horizontalLineToRelative(-80f)
            verticalLineToRelative(40f)
            horizontalLineToRelative(120f)
            verticalLineToRelative(60f)
            horizontalLineTo(360f)
            close()
            moveTo(600f, 600f)
            verticalLineToRelative(-60f)
            horizontalLineToRelative(120f)
            verticalLineToRelative(-40f)
            horizontalLineToRelative(-80f)
            verticalLineToRelative(-40f)
            horizontalLineToRelative(80f)
            verticalLineToRelative(-40f)
            horizontalLineTo(600f)
            verticalLineToRelative(-60f)
            horizontalLineToRelative(140f)
            quadToRelative(17f, 0f, 28.5f, 11.5f)
            reflectiveQuadTo(780f, 400f)
            verticalLineToRelative(160f)
            quadToRelative(0f, 17f, -11.5f, 28.5f)
            reflectiveQuadTo(740f, 600f)
            horizontalLineTo(600f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcSetupWizardPreview() {
    Icon(
        imageVector = IcSetupWizard,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = Color.Unspecified
    )
}
