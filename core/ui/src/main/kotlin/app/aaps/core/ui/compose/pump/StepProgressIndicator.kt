package app.aaps.core.ui.compose.pump

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun StepProgressIndicator(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isCompleted = i < currentStep
            val isCurrent = i == currentStep

            val circleColor by animateColorAsState(
                targetValue = when {
                    isCompleted -> primaryColor
                    isCurrent   -> primaryColor
                    else        -> outlineColor
                },
                animationSpec = tween(300),
                label = "stepColor$i"
            )

            val fillColor by animateColorAsState(
                targetValue = when {
                    isCompleted -> primaryColor
                    else        -> Color.Transparent
                },
                animationSpec = tween(300),
                label = "fillColor$i"
            )

            // Draw connecting line before circle (except for first)
            if (i > 0) {
                val lineColor by animateColorAsState(
                    targetValue = if (i <= currentStep) primaryColor else outlineColor,
                    animationSpec = tween(300),
                    label = "lineColor$i"
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                ) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // Draw circle
            Canvas(modifier = Modifier.size(16.dp)) {
                // Fill
                drawCircle(color = fillColor, radius = size.minDimension / 2)
                // Stroke
                drawCircle(
                    color = circleColor,
                    radius = size.minDimension / 2,
                    style = if (isCurrent) Stroke(width = 3.dp.toPx()) else Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
