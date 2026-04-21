package app.aaps.core.graph

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.aaps.core.graph.vico.Square
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.ui.compose.AapsTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LegendItem
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.rememberHorizontalLegend
import com.patrykandpatrick.vico.compose.common.vicoTheme

private val LegendLabelKey = ExtraStore.Key<List<String>>()

/**
 * Composable that displays basal rate profile data as a line chart using Vico charting library.
 * Supports both single profile view and side-by-side comparison of two profiles.
 *
 * **Chart Features:**
 * - Line chart with square point connectors (stepped appearance)
 * - Samples basal rates at each hour (0-24) across the day
 * - Single mode: Shows one profile line with gradient area fill (30% alpha)
 * - Comparison mode: Shows two profile lines with legend, only profile1 has area fill
 * - Vertical axis: Basal rate (U/hr)
 * - Horizontal axis: Hours of day (0-24)
 * - Zoom disabled, chart auto-fits to content
 *
 * **Visual Design:**
 * - Profile 1 line: Uses profile1Color with area fill underneath
 * - Profile 2 line: Uses profile2Color without area fill (for clarity in comparison)
 * - Legend: Horizontal pill-shaped indicators showing profile names and colors
 * - Square point connector creates stepped line appearance matching basal rate time blocks
 *
 * @param modifier Modifier for the chart container
 * @param profile1 Primary profile to display (always shown)
 * @param profile2 Optional second profile for comparison mode
 * @param profile1Name Display name for the primary profile (shown in legend for comparison)
 * @param profile2Name Display name for the second profile (required if profile2 is provided)
 * @param profile1Color Line and area fill color for profile 1 (default: theme profile1 color)
 * @param profile2Color Line color for profile 2 (default: theme profile2 color)
 *
 * @throws IllegalArgumentException if profile2 is provided but profile2Name is null
 */

@Composable
fun BasalProfileGraphCompose(
    modifier: Modifier = Modifier,
    profile1: Profile,
    profile2: Profile? = null,
    profile1Name: String,
    profile2Name: String? = null,
    profile1Color: Color = AapsTheme.profileHelperColors.profile1,
    profile2Color: Color = AapsTheme.profileHelperColors.profile2
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // Check profile2Name is provided if profile2 is provided
    require(profile2?.let { profile2Name != null } ?: true)

    LaunchedEffect(profile1, profile2) {
        // Create data with points at each hour
        val basalValues1 = (0..24).map { hour ->
            profile1.getBasalTimeFromMidnight((hour.coerceAtMost(23)) * 60 * 60)
        }

        if (profile2 != null && profile2Name != null) {
            val basalValues2 = (0..24).map { hour ->
                profile2.getBasalTimeFromMidnight((hour.coerceAtMost(23)) * 60 * 60)
            }
            modelProducer.runTransaction {
                lineSeries {
                    series(y = basalValues2)
                    series(y = basalValues1)
                }
                extras { extraStore -> extraStore[LegendLabelKey] = listOf(profile1Name, profile2Name) }
            }
        } else {
            modelProducer.runTransaction {
                lineSeries {
                    series(y = basalValues1)
                }
            }
        }
    }

    val legendItemLabelComponent = rememberTextComponent(style = TextStyle(color = vicoTheme.textColor))
    val legendIcon1 = rememberShapeComponent(fill = Fill(profile1Color))
    val legendIcon2 = rememberShapeComponent(fill = Fill(profile2Color))
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = if (profile2 != null) {
                    LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.Line(
                            fill = remember { LineCartesianLayer.LineFill.single(Fill(profile2Color)) },
                            pointConnector = Square
                        ),
                        LineCartesianLayer.Line(
                            fill = remember { LineCartesianLayer.LineFill.single(Fill(profile1Color)) },
                            areaFill = remember {
                                LineCartesianLayer.AreaFill.single(
                                    Fill(
                                        Brush.verticalGradient(
                                            listOf(
                                                profile1Color.copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                )
                            },
                            pointConnector = Square
                        )
                    )
                } else {
                    LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.Line(
                            fill = remember { LineCartesianLayer.LineFill.single(Fill(profile1Color)) },
                            areaFill = remember {
                                LineCartesianLayer.AreaFill.single(
                                    Fill(
                                        Brush.verticalGradient(
                                            listOf(
                                                profile1Color.copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                )
                            },
                            pointConnector = Square
                        )
                    )
                }
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
            legend = if (profile2 != null) {
                rememberHorizontalLegend(
                    items = { extraStore ->
                        extraStore[LegendLabelKey].forEachIndexed { index, label ->
                            add(
                                LegendItem(
                                    if (index == 0) legendIcon1 else legendIcon2,
                                    legendItemLabelComponent,
                                    label,
                                )
                            )
                        }
                    },
                    padding = Insets(top = 8.dp, start = 8.dp),
                )
            } else null
        ),
        modelProducer = modelProducer,
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}
