package app.aaps.wear.watchfaces.utils

import app.aaps.core.interfaces.rx.weardata.EventData.SingleBg
import app.aaps.core.interfaces.rx.weardata.EventData.TreatmentData.Basal
import app.aaps.core.interfaces.rx.weardata.EventData.TreatmentData.TempBasal
import app.aaps.core.interfaces.rx.weardata.EventData.TreatmentData.Treatment
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.wear.R
import com.google.common.truth.Truth.assertThat
import lecho.lib.hellocharts.model.Line
import lecho.lib.hellocharts.model.LineChartData
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.math.roundToLong

/**
 * Unit tests for [BgGraphBuilder] — the Wear watchface blood-glucose graph model builder.
 *
 * These tests exercise the deterministic branch-heavy graph math directly on the returned
 * hellocharts [LineChartData] / [Line] / PointValue model (pure-Java classes from the bundled
 * hellocharts AAR). No Android framework or Compose runtime is required.
 *
 * Time is derived from [System.currentTimeMillis] inside the constructor, so all inputs are chosen
 * with generous offsets from window boundaries and all time assertions use minute-rounded [fuzz]
 * values with a small tolerance, making them robust to real wall-clock skew during the test run.
 */
class BgGraphBuilderTest {

    // Colors — distinct sentinel ints so we can assert which line got which color.
    private val highColor = 0x111111
    private val lowColor = 0x222222
    private val midColor = 0x333333
    private val gridColour = 0x444444
    private val basalBackgroundColor = 0x555555
    private val basalCenterColor = 0x666666
    private val bolusInvalidColor = 0x777777
    private val carbsColor = 0x888888

    private val pointSize = 3
    private val timeSpan = 1

    private val minuteMs = 60_000L

    /** Reproduces BgGraphBuilder.fuzz(): (value / 60000).roundToLong().toFloat(). */
    private fun fuzz(value: Long): Float = (value / minuteMs.toDouble()).roundToLong().toFloat()

    private fun sp(
        grid: Boolean = false,
        predictions: Boolean = false,
        highlightBasals: Boolean = false,
        tempBasal: Boolean = false,
        basal: Boolean = false,
        carbs: Boolean = false,
        bolus: Boolean = false
    ): SP {
        val sp: SP = mock()
        whenever(sp.getBoolean(R.string.key_show_graph_grid, true)).thenReturn(grid)
        whenever(sp.getBoolean(R.string.key_prediction_lines, true)).thenReturn(predictions)
        whenever(sp.getBoolean(R.string.key_highlight_basals, false)).thenReturn(highlightBasals)
        whenever(sp.getBoolean(R.string.key_show_graph_temp_basal, true)).thenReturn(tempBasal)
        whenever(sp.getBoolean(R.string.key_show_graph_basal, true)).thenReturn(basal)
        whenever(sp.getBoolean(R.string.key_show_graph_carbs, true)).thenReturn(carbs)
        whenever(sp.getBoolean(R.string.key_show_graph_bolus, true)).thenReturn(bolus)
        return sp
    }

    private val dateUtil: DateUtil = mock()

    private fun bg(timeStamp: Long, sgv: Double, high: Double = 180.0, low: Double = 70.0): SingleBg =
        SingleBg(dataset = 0, timeStamp = timeStamp, sgv = sgv, high = high, low = low)

    private fun builder(
        sp: SP,
        bgDataList: List<SingleBg>,
        predictionsList: List<SingleBg> = emptyList(),
        temps: List<TempBasal> = emptyList(),
        basals: ArrayList<Basal> = ArrayList(),
        boluses: ArrayList<Treatment> = ArrayList()
    ): BgGraphBuilder =
        BgGraphBuilder(
            sp = sp,
            dateUtil = dateUtil,
            bgDataList = bgDataList,
            predictionsList = predictionsList,
            tempWatchDataList = temps,
            basalWatchDataList = basals,
            bolusWatchDataList = boluses,
            pointSize = pointSize,
            highColor = highColor,
            lowColor = lowColor,
            midColor = midColor,
            gridColour = gridColour,
            basalBackgroundColor = basalBackgroundColor,
            basalCenterColor = basalCenterColor,
            bolusInvalidColor = bolusInvalidColor,
            carbsColor = carbsColor,
            timeSpan = timeSpan
        )

    /** First 5 lines are always: highLine, lowLine, inRange, low, high. */
    private fun inRangeLine(data: LineChartData): Line = data.lines[2]
    private fun lowValuesLine(data: LineChartData): Line = data.lines[3]
    private fun highValuesLine(data: LineChartData): Line = data.lines[4]

    private fun singleY(line: Line): Float {
        assertThat(line.values).hasSize(1)
        return line.values[0].y
    }

    @Test
    fun bucketsBgValuesIntoHighInRangeAndLowByThresholds() {
        val now = System.currentTimeMillis()
        val t = now - 30 * minuteMs // safely inside (startingTime, now)
        val bgList = listOf(
            bg(t, 500.0),   // >= 450 -> high, clamped to 450
            bg(t, 200.0),   // >= highMark(180) -> high, as-is
            bg(t, 100.0),   // in range -> mid, as-is
            bg(t, 50.0),    // >= 40 -> low, as-is
            bg(t, 20.0),    // 11..39 -> low, clamped to 40
            bg(t, 5.0),     // < 11 -> dropped
            bg(now - 90 * minuteMs, 120.0), // timeStamp <= startingTime -> dropped
            bg(t, 90.0)     // in range (also the LAST entry -> supplies high=180/low=70)
        )
        val sp = sp()
        val data = builder(sp, bgList).lineData()

        // grid disabled -> no axes
        assertThat(data.axisXBottom).isNull()
        assertThat(data.axisYLeft).isNull()

        val high = highValuesLine(data)
        val inRange = inRangeLine(data)
        val low = lowValuesLine(data)

        // high bucket: 450 (clamped) + 200
        assertThat(high.values.map { it.y }).containsExactly(450f, 200f)
        // in-range bucket: 100 + 90
        assertThat(inRange.values.map { it.y }).containsExactly(100f, 90f)
        // low bucket: 50 + 40 (clamped)
        assertThat(low.values.map { it.y }).containsExactly(50f, 40f)

        // dropped values (<11 and timeStamp<=startingTime) never appear anywhere
        val allY = data.lines.flatMap { it.values }.map { it.y }
        assertThat(allY).doesNotContain(5f)
        assertThat(allY).doesNotContain(120f)
    }

    @Test
    fun bgPointCarriesFuzzedMinuteRoundedX() {
        val now = System.currentTimeMillis()
        val t = now - 25 * minuteMs
        val sp = sp()
        val data = builder(sp, listOf(bg(t, 100.0))).lineData()

        val inRange = inRangeLine(data)
        assertThat(inRange.values).hasSize(1)
        // fuzz rounds to the nearest minute; tolerate <=1 minute of wall-clock drift.
        assertThat(inRange.values[0].x).isWithin(1f).of(fuzz(t))
        assertThat(inRange.values[0].y).isEqualTo(100f)
    }

    @Test
    fun highAndLowTargetLinesUseLastReadingHighAndLow() {
        val now = System.currentTimeMillis()
        val t = now - 20 * minuteMs
        // last entry defines the marks (high=210, low=65)
        val bgList = listOf(
            bg(t, 120.0, high = 180.0, low = 70.0),
            bg(t, 130.0, high = 210.0, low = 65.0)
        )
        val sp = sp()
        val data = builder(sp, bgList).lineData()

        val highLine = data.lines[0]
        val lowLine = data.lines[1]

        // both target lines are horizontal: 2 points at the mark value
        assertThat(highLine.values.map { it.y }).containsExactly(210f, 210f)
        assertThat(lowLine.values.map { it.y }).containsExactly(65f, 65f)
        assertThat(highLine.color).isEqualTo(highColor)
        assertThat(lowLine.color).isEqualTo(lowColor)
        assertThat(highLine.hasPoints()).isFalse()
        assertThat(lowLine.hasPoints()).isFalse()

        // both marks (130 -> highMark 210? no: 130 < 210 and >= 65 -> in range) reclassify:
        // 120 in [65,210) in range, 130 in [65,210) in range
        assertThat(inRangeLine(data).values.map { it.y }).containsExactly(120f, 130f)
    }

    @Test
    fun bgLineStylingMatchesColorsAndPointRadius() {
        val now = System.currentTimeMillis()
        val t = now - 15 * minuteMs
        val sp = sp()
        val data = builder(sp, listOf(bg(t, 100.0))).lineData()

        val inRange = inRangeLine(data)
        val low = lowValuesLine(data)
        val high = highValuesLine(data)

        assertThat(inRange.color).isEqualTo(midColor)
        assertThat(low.color).isEqualTo(lowColor)
        assertThat(high.color).isEqualTo(highColor)
        for (l in listOf(inRange, low, high)) {
            assertThat(l.pointRadius).isEqualTo(pointSize)
            assertThat(l.hasPoints()).isTrue()
            assertThat(l.hasLines()).isFalse()
        }
    }

    @Test
    fun predictionLinesClampToUpperCutoffAndGroupByColor() {
        val now = System.currentTimeMillis()
        val futA = now + 10 * minuteMs
        val futB = now + 15 * minuteMs
        val colorA = 0xAA0000
        val colorB = 0xBB0000
        // sgv=500 clamps to UPPER_CUTOFF_SGV=400; sgv=120 stays; two colors -> two lines.
        val predictions = listOf(
            SingleBg(dataset = 0, timeStamp = futA, sgv = 500.0, high = 180.0, low = 70.0, color = colorA),
            SingleBg(dataset = 0, timeStamp = futB, sgv = 120.0, high = 180.0, low = 70.0, color = colorA),
            SingleBg(dataset = 0, timeStamp = futA, sgv = 300.0, high = 180.0, low = 70.0, color = colorB)
        )
        // one in-range bg so bgDataList is non-empty
        val bgList = listOf(bg(now - 10 * minuteMs, 100.0))
        val sp = sp(predictions = true)
        val data = builder(sp, bgList, predictionsList = predictions).lineData()

        // Prediction lines come after the 5 bg lines. bolusInvalidLine() is appended unconditionally
        // (empty here since no boluses were supplied), so filter to the non-empty prediction lines.
        val predictionLines = data.lines.drop(5).filter { it.values.isNotEmpty() }
        val byColor = predictionLines.associateBy { it.color }
        assertThat(byColor.keys).containsExactly(colorA, colorB)

        val lineA = byColor.getValue(colorA)
        assertThat(lineA.values.map { it.y }).containsExactly(400f, 120f) // 500 clamped -> 400
        val lineB = byColor.getValue(colorB)
        assertThat(singleY(lineB)).isEqualTo(300f)

        // prediction line styling
        assertThat(lineA.hasLines()).isFalse()
        assertThat(lineA.hasPoints()).isTrue()
        assertThat(lineA.pointRadius).isEqualTo(pointSize / 2) // 3/2 = 1
    }

    @Test
    fun bolusSmbCarbsAndInvalidPointsSelectedByWindowAndPredicates() {
        val now = System.currentTimeMillis()
        val inWin = now - 5 * minuteMs   // within (startingTime+1)..endingTime
        val outOld = now - 120 * minuteMs // before startingTime -> excluded from all
        val bgList = listOf(bg(now - 10 * minuteMs, 100.0))

        val boluses = arrayListOf(
            Treatment(date = inWin, bolus = 1.5, carbs = 0.0, isSMB = false, isValid = true), // -> bolusLine
            Treatment(date = inWin, bolus = 0.4, carbs = 0.0, isSMB = true, isValid = true),  // -> smbLine
            Treatment(date = inWin, bolus = 0.0, carbs = 20.0, isSMB = false, isValid = true),// -> carbsLine
            Treatment(date = inWin, bolus = 0.0, carbs = 0.0, isSMB = false, isValid = false),// -> invalid
            Treatment(date = outOld, bolus = 2.0, carbs = 0.0, isSMB = false, isValid = true) // out of window
        )
        // enable carbs + bolus (bolus also draws smb); temp/basal/predictions/grid off
        val sp = sp(carbs = true, bolus = true)
        val data = builder(sp, bgList, boluses = boluses).lineData()

        // With grid/temp/predictions/basal off, order after 5 bg lines is:
        // [5]=bolusInvalidLine, [6]=carbsLine, [7]=bolusLine, [8]=smbLine
        val invalidLine = data.lines[5]
        val carbsL = data.lines[6]
        val bolusL = data.lines[7]
        val smbL = data.lines[8]

        assertThat(invalidLine.color).isEqualTo(bolusInvalidColor)
        assertThat(invalidLine.values).hasSize(1) // only the isValid=false treatment

        assertThat(carbsL.color).isEqualTo(carbsColor)
        assertThat(carbsL.values).hasSize(1)
        assertThat(carbsL.pointRadius).isEqualTo(pointSize * 2)

        assertThat(bolusL.color).isEqualTo(basalCenterColor)
        assertThat(bolusL.values).hasSize(1) // only the non-SMB positive bolus in window
        assertThat(bolusL.pointRadius).isEqualTo(pointSize * 2)

        assertThat(smbL.values).hasSize(1) // only the SMB positive bolus in window
        assertThat(smbL.pointRadius).isEqualTo(pointSize)

        // window filtering: the old bolus X must not appear on bolusLine
        assertThat(bolusL.values.map { it.x }).doesNotContain(fuzz(outOld))
        assertThat(bolusL.values[0].x).isWithin(1f).of(fuzz(inWin))
    }

    @Test
    fun predictionEnabledExtendsEndingTimeSoTargetLinesReachFurther() {
        val now = System.currentTimeMillis()
        val bgList = listOf(bg(now - 10 * minuteMs, 100.0))
        // prediction 20 min out (<= cap of 36 min for timeSpan=1) extends endingTime beyond the
        // default now+6min.
        val predictions = listOf(
            SingleBg(dataset = 0, timeStamp = now + 20 * minuteMs, sgv = 110.0, high = 180.0, low = 70.0, color = 0xAA0000)
        )

        val withPred = builder(sp(predictions = true), bgList, predictionsList = predictions).lineData()
        val noPred = builder(sp(predictions = false), bgList, predictionsList = predictions).lineData()

        // highLine second point X == fuzz(endingTime). With predictions, endingTime is extended.
        val endWithPred = withPred.lines[0].values[1].x
        val endNoPred = noPred.lines[0].values[1].x

        assertThat(endWithPred).isGreaterThan(endNoPred)
        // default endingTime ~ now + 6 min; extended ~ now + 20 min
        assertThat(endWithPred).isWithin(1f).of(fuzz(now + 20 * minuteMs))
        assertThat(endNoPred).isWithin(1f).of(fuzz(now + 6 * minuteMs))
    }
}
