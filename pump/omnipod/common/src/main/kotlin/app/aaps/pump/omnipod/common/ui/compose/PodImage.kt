package app.aaps.pump.omnipod.common.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.aaps.pump.omnipod.common.R

@Composable
fun PodImage(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_pod),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .graphicsLayer {
                rotationX = 180f
                rotationY = 180f
            }
    )
}
