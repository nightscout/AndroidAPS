package app.aaps.core.graph

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.time.T
import app.aaps.core.objects.extensions.iobCalc
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
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
import kotlin.math.floor
import app.aaps.core.ui.R as CoreUiR

private val InsulinLegendLabelKey = ExtraStore.Key<List<String>>()

private val ActivityColor = Color(0xFF1976D2)  // Blue
private val IobColor = Color(0xFFE040FB)       // Magenta

/**
 * Composable that displays insulin activity and IOB curves using Vico charting library.
 *
 * Uses dual Y-axis layout:
 * - Activity curve (blue, left Y-axis): insulin activity contribution over time
 * - IOB curve (magenta with area fill, right Y-axis): remaining insulin on board
 *
 * X-axis: time in minutes from 0 to DIA + 1 hour
 * Data sampled at 5-minute intervals using iobCalc()
 *
 * @param iCfg Insulin configuration to visualize
 * @param diaSample Optional DIA override in hours (for preview while editing)
 * @param modifier Modifier for the chart container
 */
@Composable
fun InsulinGraphCompose(
    iCfg: ICfg,
    diaSample: Double? = null,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val activityLabel = stringResource(CoreUiR.string.activity)
    val iobLabel = stringResource(CoreUiR.string.iob)

    LaunchedEffect(iCfg.insulinPeakTime, iCfg.insulinEndTime, iCfg.concentration, diaSample) {
        val dia = diaSample ?: iCfg.dia
        val hours = floor(dia + 1).toLong()
        val bolus = BS(
            timestamp = 0,
            amount = 1.0,
            type = BS.Type.NORMAL,
            iCfg = iCfg
        )

        val activityValues = mutableListOf<Double>()
        val iobValues = mutableListOf<Double>()
        var time = 0L
        while (time <= T.hours(hours).msecs()) {
            val iob = bolus.iobCalc(time)
            activityValues.add(iob.activityContrib)
            iobValues.add(iob.iobContrib)
            time += T.mins(5).msecs()
        }

        // Normalize activity to 0–1 range so both curves are visible
        val maxActivity = activityValues.maxOrNull() ?: 1.0
        val normalizedActivity = if (maxActivity > 0) activityValues.map { it / maxActivity } else activityValues

        modelProducer.runTransaction {
            // Block 1 → IOB layer (layer 0, primary)
            lineSeries {
                series(y = iobValues)
            }
            // Block 2 → Activity layer (layer 1, normalized)
            lineSeries {
                series(y = normalizedActivity)
            }
            extras { extraStore ->
                extraStore[InsulinLegendLabelKey] = listOf(iobLabel, activityLabel)
            }
        }
    }

    val legendItemLabelComponent = rememberTextComponent(style = TextStyle(color = vicoTheme.textColor))
    val activityLegendIcon = rememberShapeComponent(fill = Fill(ActivityColor))
    val iobLegendIcon = rememberShapeComponent(fill = Fill(IobColor))

    CartesianChartHost(
        chart = rememberCartesianChart(
            // Layer 0: IOB (primary, left Y-axis with labels)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(
                        fill = remember { LineCartesianLayer.LineFill.single(Fill(IobColor)) },
                        areaFill = remember {
                            LineCartesianLayer.AreaFill.single(
                                Fill(
                                    Brush.verticalGradient(
                                        listOf(
                                            IobColor.copy(alpha = 0.2f),
                                            Color.Transparent
                                        )
                                    )
                                )
                            )
                        }
                    )
                ),
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 1.0) },
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 1: Activity (no dedicated axis, scaled to IOB range)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(
                        fill = remember { LineCartesianLayer.LineFill.single(Fill(ActivityColor)) },
                        areaFill = remember {
                            LineCartesianLayer.AreaFill.single(
                                Fill(
                                    Brush.verticalGradient(
                                        listOf(
                                            ActivityColor.copy(alpha = 0.3f),
                                            Color.Transparent
                                        )
                                    )
                                )
                            )
                        }
                    )
                ),
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 1.0) },
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
            legend = rememberHorizontalLegend(
                items = { extraStore ->
                    extraStore[InsulinLegendLabelKey].forEachIndexed { index, label ->
                        add(
                            LegendItem(
                                if (index == 0) iobLegendIcon else activityLegendIcon,
                                legendItemLabelComponent,
                                label,
                            )
                        )
                    }
                },
                padding = Insets(top = 8.dp, start = 8.dp),
            )
        ),
        modelProducer = modelProducer,
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}
