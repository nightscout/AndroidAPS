package app.aaps.ui.compose.siteRotationDialog

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.aaps.ui.compose.siteRotationDialog.viewModels.BodyType

@Composable
fun BodyView(
    bodyType: BodyType,
    isFrontView: Boolean,
    modifier: Modifier = Modifier
) {
    val imageVector = if (isFrontView) bodyType.frontImage else bodyType.backImage
    val aspectRatio = imageVector.viewportWidth / imageVector.viewportHeight
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = modifier.aspectRatio(aspectRatio),
        tint = Color.Unspecified
    )
}