package app.aaps.core.graph

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
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
import com.patrykandpatrick.vico.compose.common.LegendItem
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.rememberHorizontalLegend
import com.patrykandpatrick.vico.compose.common.vicoTheme

/**
 * Composable that displays target blood glucose range profile data as a line chart using Vico charting library.
 * Supports both single profile view and side-by-side comparison of two profiles.
 */

private val LegendLabelKey = ExtraStore.Key<List<String>>()

private fun fromMgdlToUnits(value: Double, units: GlucoseUnit): Double =
    if (units == GlucoseUnit.MGDL) value else value * Constants.MGDL_TO_MMOLL

@Composable
fun TargetBgProfileGraphCompose(
    modifier: Modifier = Modifier,
    profile1: Profile,
    profile2: Profile? = null,
    profile1Name: String,
    profile2Name: String? = null,
    profile1Color: Color = AapsTheme.profileHelperColors.profile1,
    profile2Color: Color = AapsTheme.profileHelperColors.profile2
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    require(profile2?.let { profile2Name != null } ?: true)

    LaunchedEffect(profile1, profile2) {
        val units = profile1.units
        val targetLowValues1 = (0..24).map { hour ->
            fromMgdlToUnits(
                profile1.getTargetLowMgdlTimeFromMidnight((hour.coerceAtMost(23)) * 60 * 60),
                units
            )
        }
        val targetHighValues1 = (0..24).map { hour ->
            fromMgdlToUnits(
                profile1.getTargetHighMgdlTimeFromMidnight((hour.coerceAtMost(23)) * 60 * 60),
                units
            )
        }

        if (profile2 != null && profile2Name != null) {
            val targetLowValues2 = (0..24).map { hour ->
                fromMgdlToUnits(
                    profile2.getTargetLowMgdlTimeFromMidnight((hour.coerceAtMost(23)) * 60 * 60),
                    units
                )
            }
            val targetHighValues2 = (0..24).map { hour ->
                fromMgdlToUnits(
                    profile2.getTargetHighMgdlTimeFromMidnight((hour.coerceAtMost(23)) * 60 * 60),
                    units
                )
            }
            modelProducer.runTransaction {
                lineSeries {
                    series(y = targetHighValues2)
                    series(y = targetLowValues2)
                    series(y = targetHighValues1)
                    series(y = targetLowValues1)
                }
                extras { extraStore -> extraStore[LegendLabelKey] = listOf(profile1Name, profile2Name) }
            }
        } else {
            modelProducer.runTransaction {
                lineSeries {
                    series(y = targetHighValues1)
                    series(y = targetLowValues1)
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
                            fill = remember { LineCartesianLayer.LineFill.single(Fill(profile2Color)) },
                            pointConnector = Square
                        ),
                        LineCartesianLayer.Line(
                            fill = remember { LineCartesianLayer.LineFill.single(Fill(profile1Color)) },
                            pointConnector = Square
                        ),
                        LineCartesianLayer.Line(
                            fill = remember { LineCartesianLayer.LineFill.single(Fill(profile1Color)) },
                            pointConnector = Square
                        )
                    )
                } else {
                    LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.Line(
                            fill = remember { LineCartesianLayer.LineFill.single(Fill(profile1Color)) },
                            pointConnector = Square
                        ),
                        LineCartesianLayer.Line(
                            fill = remember { LineCartesianLayer.LineFill.single(Fill(profile1Color)) },
                            areaFill = remember {
                                LineCartesianLayer.AreaFill.single(
                                    Fill(profile1Color.copy(alpha = 0.2f))
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
