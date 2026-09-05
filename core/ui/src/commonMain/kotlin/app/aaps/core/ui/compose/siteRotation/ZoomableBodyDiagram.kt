package app.aaps.core.ui.compose.siteRotation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import app.aaps.core.data.model.TE

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 3f

/**
 * Zoomable container for front+back body diagrams side by side.
 * - Pinch to zoom (1x–3x)
 * - Drag to pan when zoomed in
 * - Double-tap to reset to 1x
 *
 * Zone taps are handled by [BodyView] internally.
 */
@Composable
fun ZoomableBodyDiagram(
    filteredLocationColor: List<TE>,
    showPumpSites: Boolean,
    showCgmSites: Boolean,
    selectedLocation: TE.Location,
    bodyType: BodyType,
    onZoneClick: (TE.Location) -> Unit,
    modifier: Modifier = Modifier,
    editedType: TE.Type? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    scale = newScale

                    if (newScale > 1f) {
                        val maxOffsetX = (size.width * (newScale - 1)) / 2
                        val maxOffsetY = (size.height * (newScale - 1)) / 2
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        ) {
            BodyView(
                filteredLocationColor = filteredLocationColor,
                showPumpSites = showPumpSites,
                showCgmSites = showCgmSites,
                selectedLocation = selectedLocation,
                bodyType = bodyType,
                isFrontView = true,
                onZoneClick = onZoneClick,
                modifier = Modifier.weight(1f),
                editedType = editedType
            )
            BodyView(
                filteredLocationColor = filteredLocationColor,
                showPumpSites = showPumpSites,
                showCgmSites = showCgmSites,
                selectedLocation = selectedLocation,
                bodyType = bodyType,
                isFrontView = false,
                onZoneClick = onZoneClick,
                modifier = Modifier.weight(1f),
                editedType = editedType
            )
        }
    }
}
