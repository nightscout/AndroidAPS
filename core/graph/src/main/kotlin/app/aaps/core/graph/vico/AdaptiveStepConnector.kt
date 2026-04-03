package app.aaps.core.graph.vico

import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.PointConnector
import kotlin.math.abs

/**
 * Adaptive PointConnector that switches between step and line connectors based on slope.
 *
 * **Behavior:**
 * - Calculates y:x ratio (slope) between two consecutive points
 * - If |dy/dx| > 1.0 (>45° angle, steep): Uses step connector (horizontal→vertical)
 * - If |dy/dx| ≤ 1.0 (≤45° angle, gradual): Uses straight line connector
 *
 * **Use Case:**
 * Ideal for graphs like COB where:
 * - Steep changes (carb absorption, sharp drops) should show as discrete steps
 * - Gradual changes (slow carb entry) should show as smooth lines
 *
 * **Calculation:**
 * - Uses simple ratio instead of trigonometry: |dy/dx|
 * - 45° threshold: tan(45°) = 1, so ratio > 1.0 means angle > 45°
 * - Fast: just division and comparison, no atan2 or Math.toDegrees
 *
 * **Visual Examples:**
 * ```
 * Steep (>45°) - Step:           Gradual (≤45°) - Line:
 * •——                             •
 *    |                           /
 *    |                          /
 *    •                         •
 * ```
 */
val AdaptiveStep: PointConnector = PointConnector { _, path, x1, y1, x2, y2 ->
    // Calculate slope ratio: |dy/dx|
    val dx = abs(x2 - x1)
    val dy = abs(y2 - y1)

    // Avoid division by zero
    val isSteep = if (dx > 0.0001f) {
        (dy / dx) > 1.0f  // 45° threshold: tan(45°) = 1
    } else {
        true  // Vertical line (dx ≈ 0) is always steep
    }

    if (isSteep) {
        // Step connector: horizontal then vertical
        path.lineTo(x2, y1)  // Horizontal to new x-coordinate at current y-level
        path.lineTo(x2, y2)  // Vertical from current position to new y-level
    } else {
        // Default connector: straight line
        path.lineTo(x2, y2)
    }
}
