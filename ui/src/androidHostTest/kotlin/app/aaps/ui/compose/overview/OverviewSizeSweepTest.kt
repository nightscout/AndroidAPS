package app.aaps.ui.compose.overview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TDD
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.interfaces.utils.TimeDiff
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.OverviewViewModelFixture
import app.aaps.ui.compose.testing.OverviewViewModelFixture.Companion.NOW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.min
import kotlin.math.roundToInt
import app.aaps.core.ui.R as CoreUiR

/**
 * The overview at every window size from a small phone to a big tablet, in every shape from very wide
 * to very tall, with the normal and a large system font.
 *
 * Issue #5355 was found on one folding phone, but the fault was general: whatever did not fit was cut
 * or broken instead of giving way. So this does not test a list of devices - the next new screen is
 * somewhere in this range. On every size it checks what must hold everywhere:
 *  - no text breaks inside a word ("Cann / ula"),
 *  - no text is cut off without an ellipsis ("23:36(" for "23:36(-1)"),
 *  - no text is taller than the place it has (the second line of a chip cut in half).
 *
 * The status rows have the widest age on purpose, "8d23h", and a cannula usage beside it.
 *
 * The window is a fake: [LocalConfiguration] says the size and the orientation, which is what the
 * layout and the text scale read on Android, and the content is given exactly that size. The real
 * Robolectric window is larger than every size here, so nothing is clipped by the test itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1700dp-h1700dp")
class OverviewSizeSweepTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var screen: AapsScreenFixture
    private lateinit var viewModels: OverviewViewModelFixture

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        screen = AapsScreenFixture()
        viewModels = OverviewViewModelFixture(screen)
        viewModels.withBg()
        withLongStatusAges()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `nothing is cut or broken at any window size`() {
        assertNoProblems(sweep(expandStatus = false))
    }

    @Test
    fun `the expanded status rows fit at any window size`() {
        // The stacked layout starts with the status card collapsed, the wide ones expanded. This opens
        // it everywhere, so the rows are checked at phone widths too.
        assertNoProblems(sweep(expandStatus = true))
    }

    private fun assertNoProblems(problems: List<String>) {
        if (problems.isNotEmpty()) throw AssertionError("${problems.size} problems:\n" + problems.joinToString("\n"))
    }

    private fun sweep(expandStatus: Boolean): List<String> {
        val status = viewModels.awaitStatusItems()
        runBlocking { withTimeout(5_000) { status.uiState.first { it.cannulaStatus?.level != null } } }
        var window by mutableStateOf(Window(widthDp = 400, heightDp = 800, fontScale = 1f))
        compose.setContent {
            AtWindowSize(window) {
                screen.Content { Overview(status = status) }
            }
        }
        val expandLabel = RuntimeEnvironment.getApplication().getString(CoreUiR.string.expand)
        val problems = mutableListOf<String>()
        for (fontScale in FONT_SCALES) {
            for (width in WIDTHS) {
                for (shape in SHAPES) {
                    val height = (width * shape).roundToInt().coerceIn(MIN_SIDE, MAX_SIDE)
                    window = Window(width, height, fontScale)
                    compose.waitForIdle()
                    if (expandStatus) {
                        compose.onAllNodesWithContentDescription(expandLabel).fetchSemanticsNodes().forEach { _ ->
                            compose.onAllNodesWithContentDescription(expandLabel)[0].performClick()
                            compose.waitForIdle()
                        }
                    }
                    problems += textProblems("$width x $height dp, font $fontScale")
                }
            }
        }
        return problems.distinct()
    }

    /** Every text on the screen that is broken inside a word, cut without an ellipsis, or too tall. */
    private fun textProblems(where: String): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                val shown = node.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty()
                results.mapNotNull { result -> problemOf(result)?.let { "$where: \"${shown.ifEmpty { result.layoutInput.text.text }}\" $it" } }
            }

    private fun problemOf(result: TextLayoutResult): String? {
        val text = result.layoutInput.text.text
        val lastLine = result.lineCount - 1
        // Not didOverflowHeight: that is also true for a text cut with an ellipsis on purpose. This is the
        // box being lower than the lines in it, so they are cut at the top or the bottom.
        if (result.size.height < result.multiParagraph.height) {
            return "is taller than its place: ${result.multiParagraph.height.roundToInt()} px high in ${result.size.height} px, " +
                "${result.size.width} px wide"
        }
        // Not didOverflowWidth: a centred text reports that even when it fits (see ActionButtonsTest).
        if (result.multiParagraph.didExceedMaxLines && !result.isLineEllipsized(lastLine)) return "is cut off without an ellipsis"
        for (line in 0 until lastLine) {
            val end = result.getLineEnd(line)
            if (end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()) {
                return "breaks inside a word, after \"${text.substring(0, end)}\""
            }
        }
        return null
    }

    /** "8d23h" on every status row and a cannula usage, like the screen in issue #5355. */
    private fun withLongStatusAges() {
        val changedAt = NOW - 8 * DAY - 23 * HOUR
        runBlocking {
            for (type in listOf(TE.Type.CANNULA_CHANGE, TE.Type.INSULIN_CHANGE, TE.Type.SENSOR_CHANGE, TE.Type.PUMP_BATTERY_CHANGE)) {
                whenever(viewModels.persistenceLayer.getLastTherapyRecordUpToNow(type))
                    .thenReturn(TE(timestamp = changedAt, type = type, glucoseUnit = GlucoseUnit.MGDL))
            }
            whenever(viewModels.tddCalculator.calculateIntervalWithCachedDays(any(), any(), any()))
                .thenReturn(TDD(timestamp = NOW, totalAmount = 428.0))
        }
        whenever(screen.dateUtil.computeDiff(any(), any())).thenAnswer {
            val ms = it.getArgument<Long>(1) - it.getArgument<Long>(0)
            TimeDiff(days = ms / DAY, hours = ms % DAY / HOUR, minutes = 0, seconds = 0, milliseconds = 0, microseconds = 0, nanoseconds = 0)
        }
        // Shows the battery row, with its age
        viewModels.pumpDescription.isBatteryReplaceable = true
    }

    @Composable
    private fun Overview(status: StatusViewModel) {
        OverviewScreen(
            profileName = PROFILE_NAME, isProfileModified = true, profileProgress = 0.3f,
            tempTargetText = TT_TEXT, tempTargetState = TempTargetChipState.Active,
            tempTargetProgress = 0.5f, tempTargetReason = null,
            runningMode = RM.Mode.CLOSED_LOOP, runningModeText = RM_TEXT,
            runningModeRemaining = "", runningModeProgress = 0f,
            tbrState = TbrState.NONE, smbEnabled = true, isSimpleMode = false, calcProgress = 100,
            graphViewModel = viewModels.graphViewModel, chipsViewModel = viewModels.chipsViewModel,
            manageViewModel = viewModels.manageViewModel, statusViewModel = status,
            statusLightsDef = PreferenceSubScreenDef(key = "statuslights", titleResId = CoreUiR.string.statuslights),
            onNavigate = {}, onTbrChipClick = {}, onIobChipClick = {},
            notifications = emptyList(), onDismissNotification = {}, onNotificationActionClick = {},
            autoShowNotificationSheet = false, onAutoShowConsumed = {},
            paddingValues = PaddingValues(0.dp)
        )
    }

    private data class Window(val widthDp: Int, val heightDp: Int, val fontScale: Float)

    /** Shows [content] as if the screen were [window]: its size, its orientation and its font size. */
    @Composable
    private fun AtWindowSize(window: Window, content: @Composable () -> Unit) {
        val base = LocalConfiguration.current
        val configuration = remember(window) {
            Configuration(base).apply {
                screenWidthDp = window.widthDp
                screenHeightDp = window.heightDp
                smallestScreenWidthDp = min(window.widthDp, window.heightDp)
                orientation = if (window.widthDp > window.heightDp) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                fontScale = window.fontScale
            }
        }
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalConfiguration provides configuration,
            LocalDensity provides Density(density.density, window.fontScale)
        ) {
            Box(Modifier.requiredSize(window.widthDp.dp, window.heightDp.dp)) { content() }
        }
    }

    companion object {

        private const val DAY = 24 * 60 * 60 * 1000L
        private const val HOUR = 60 * 60 * 1000L

        /** From a small phone to a big tablet. */
        private val WIDTHS = 320..1600 step 80

        /** Height as a part of the width: very wide, landscape, portrait and a tall phone. */
        private val SHAPES = listOf(0.45f, 0.75f, 1.33f, 2.1f)

        private const val MIN_SIDE = 320
        private const val MAX_SIDE = 1600

        /** The normal font, and a large one as some users set it. */
        private val FONT_SCALES = listOf(1f, 1.3f)

        // Long on purpose: the texts from the screen in issue #5355
        private const val PROFILE_NAME = "Vsedni den copy (95%)"
        private const val TT_TEXT = "8,0 (55')"
        private const val RM_TEXT = "Closed Loop"
    }
}
