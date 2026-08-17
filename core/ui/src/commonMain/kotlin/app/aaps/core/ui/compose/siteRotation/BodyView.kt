package app.aaps.core.ui.compose.siteRotation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import app.aaps.core.data.model.TE

@Composable
fun BodyView(
    filteredLocationColor: List<TE>,
    showPumpSites: Boolean,
    showCgmSites: Boolean,
    selectedLocation: TE.Location,
    bodyType: BodyType,
    isFrontView: Boolean,
    onZoneClick: (TE.Location) -> Unit,
    modifier: Modifier = Modifier,
    editedType: TE.Type? = null
) {
    val background = if (isFrontView) bodyType.frontImage else bodyType.backImage
    val zones = if (isFrontView) bodyType.frontZones else bodyType.backZones
    val aspectRatio = background.viewportWidth / background.viewportHeight

    val zoneColors = remember(filteredLocationColor, showPumpSites, showCgmSites) {
        computeZoneColors(filteredLocationColor, showPumpSites, showCgmSites)
    }

    fun showLocation(location: TE.Location): Boolean =
        location != TE.Location.NONE && (
            if (editedType == TE.Type.CANNULA_CHANGE) location.pump
            else (showPumpSites && location.pump) || showCgmSites
        )

    BoxWithConstraints(modifier = modifier.aspectRatio(aspectRatio)) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()
        val viewportWidth = background.viewportWidth
        val viewportHeight = background.viewportHeight

        Icon(
            imageVector = background,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            tint = Color.Unspecified
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zones, showPumpSites, showCgmSites, editedType) {
                    detectTapGestures { offset ->
                        val x = offset.x * viewportWidth / canvasWidth
                        val y = offset.y * viewportHeight / canvasHeight
                        val tapPoint = Offset(x, y)

                        zones.reversed().forEach { (location, path) ->
                            if (path.containsPoint(tapPoint.x, tapPoint.y) && showLocation(location)) {
                                onZoneClick(location)
                                return@detectTapGestures
                            }
                        }
                        onZoneClick(TE.Location.NONE)
                    }
                }
        ) {
            val scaleX = size.width / viewportWidth
            val scaleY = size.height / viewportHeight

            val matrix = Matrix()
            matrix.scale(scaleX, scaleY)

            zones.forEach { (location, path) ->
                if (showLocation(location)) {
                    val originalComposePath = path
                    val transformedPath = Path().apply {
                        addPath(originalComposePath)
                        transform(matrix)
                    }
                    val baseColor = zoneColors[location] ?: Color.LightGray
                    drawPath(
                        path = transformedPath,
                        color = if (location == selectedLocation) Color(0xFF66FF66) else baseColor,
                        style = Fill
                    )
                    drawPath(
                        path = transformedPath,
                        color = Color.Black,
                        style = Stroke(width = 0.2835f * scaleX)
                    )
                }
            }
        }
    }
}

/**
 * Whether a tap landed inside a body zone.
 *
 * The original used `android.graphics.Region`, with a note that `composePath.contains(...)` could
 * not be made to work. That note was right, and so is the reason: Compose's `Path` has no point
 * test. Intersecting with a tiny rectangle is the portable way to ask the same question, and unlike
 * the `Region` version it does not round the tap to whole pixels - `Region` works in integers, so
 * the old code truncated the coordinates before testing.
 */
fun Path.containsPoint(x: Float, y: Float): Boolean {
    val probe = Path().apply { addRect(Rect(x - 0.5f, y - 0.5f, x + 0.5f, y + 0.5f)) }
    val hit = Path()
    hit.op(this, probe, PathOperation.Intersect)
    return !hit.isEmpty
}

internal fun computeZoneColors(
    entries: List<TE>,
    showPumpSites: Boolean,
    showCgmSites: Boolean
): Map<TE.Location, Color> {
    val cannulaEvents = entries
        .filter { it.type == TE.Type.CANNULA_CHANGE }
        .sortedByDescending { it.timestamp }

    val sensorEvents = entries
        .filter { it.type == TE.Type.SENSOR_CHANGE }
        .sortedByDescending { it.timestamp }

    val cannulaPositions = cannulaEvents
        .groupBy { it.location }
        .mapValues { (_, events) ->
            val latest = events.maxByOrNull { it.timestamp } ?: return@mapValues null
            cannulaEvents.indexOfFirst { it.timestamp == latest.timestamp }
        }
        .filterValues { it != null }
        .mapValues { it.value!! }

    val sensorPositions = sensorEvents
        .groupBy { it.location }
        .mapValues { (_, events) ->
            val latest = events.maxByOrNull { it.timestamp } ?: return@mapValues null
            sensorEvents.indexOfFirst { it.timestamp == latest.timestamp }
        }
        .filterValues { it != null }
        .mapValues { it.value!! }

    fun getSmoothColor(fraction: Float): Color {
        val colors = listOf(
            Color.Red,
            Color(0xFFFFA500),
            Color.Yellow,
            Color(0xFF92C850),
            Color.LightGray
        )
        return when {
            fraction <= 0f -> colors[0]
            fraction > 1f -> colors[4]

            fraction < 0.25f -> {
                val f = fraction / 0.25f
                interpolateColor(colors[0], colors[1], f)
            }

            fraction < 0.50f -> {
                val f = (fraction - 0.25f) / 0.25f
                interpolateColor(colors[1], colors[2], f)
            }

            else -> {
                val f = (fraction - 0.50f) / 0.50f
                interpolateColor(colors[2], colors[3], f)
            }
        }
    }

    val colors = mutableMapOf<TE.Location, Color>()
    val allLocations = (cannulaPositions.keys + sensorPositions.keys).toSet()

    allLocations.forEach { location ->
        val cannulaFraction = cannulaPositions[location]?.let { pos ->
            pos.toFloat() / (cannulaEvents.size - 1).coerceAtLeast(14)
        } ?: 1.5f

        val sensorFraction = sensorPositions[location]?.let { pos ->
            pos.toFloat() / (sensorEvents.size - 1).coerceAtLeast(4)
        } ?: 1.5f

        val color = when {
            !showPumpSites && !showCgmSites -> Color.Transparent

            showPumpSites && showCgmSites   -> {
                if (cannulaFraction < sensorFraction) getSmoothColor(cannulaFraction)
                else getSmoothColor(sensorFraction)
            }

            showPumpSites                   -> getSmoothColor(cannulaFraction)
            showCgmSites                    -> getSmoothColor(sensorFraction)
            else                            -> Color.Transparent
        }
        location?.let { colors[it] = color }
    }
    return colors
}

private fun interpolateColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = 1f
    )
}
