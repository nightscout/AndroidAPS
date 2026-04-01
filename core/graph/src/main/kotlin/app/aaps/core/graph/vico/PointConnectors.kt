package app.aaps.core.graph.vico

import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.PointConnector

/**
 * Custom Vico PointConnector that creates a stepped (staircase) line appearance.
 *
 * This connector draws horizontal-then-vertical segments between data points instead of
 * diagonal lines, creating a "square" or "stepped" visual effect. This is ideal for
 * displaying profile data that changes at discrete time blocks rather than gradually.
 *
 * **Visual Behavior:**
 * - From point (x1, y1) to point (x2, y2):
 *   1. Draw horizontal line from (x1, y1) to (x2, y1)
 *   2. Draw vertical line from (x2, y1) to (x2, y2)
 * - Creates right-angle corners instead of diagonal connections
 * - Results in a stepped/staircase appearance
 *
 * **Use Cases in AndroidAPS:**
 * - Basal rate profiles (rates change at specific times, not gradually)
 * - IC (Insulin to Carb) ratios (discrete time blocks with different ratios)
 * - ISF (Insulin Sensitivity Factor) profiles (sensitivity changes at specific times)
 * - Target BG ranges (target values change at discrete time boundaries)
 *
 * This visual representation accurately reflects how profile values actually work in
 * insulin therapy: values remain constant within a time block and change abruptly at
 * block boundaries, rather than transitioning smoothly.
 *
 * **Example:**
 * ```
 * Diagonal connector:     Square connector (this):
 *     •                        •——
 *    /                              |
 *   /                               |
 *  •                                •
 * ```
 */
val Square: PointConnector = PointConnector { _, path, _, y1, x2, y2 ->
    path.lineTo(x2, y1)  // Draw horizontal line to new x-coordinate at current y-level
    path.lineTo(x2, y2)  // Draw vertical line from current position to new y-level
}

