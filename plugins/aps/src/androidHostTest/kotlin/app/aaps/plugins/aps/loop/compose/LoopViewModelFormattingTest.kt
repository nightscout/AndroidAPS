package app.aaps.plugins.aps.loop.compose

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Pins the exact text [LoopViewModel] puts into [LoopUiState].
 *
 * `toPlainText` builds every line of the "set by pump" cards and `updateState` fills the rest of the
 * screen. Both were untested. They are the parts a user actually reads, so they are the parts that
 * must survive the move to commonMain letter for letter.
 *
 * The resolver here is hand written rather than mocked on purpose. It answers a resource id and a
 * [TextRef] with the *same* word, so every assertion below is about the produced text and not about
 * which resolver overload the view model happened to call.
 */
internal class LoopViewModelFormattingTest {

    @Mock private lateinit var loop: Loop
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var decimalFormatter: DecimalFormatter

    private val updateGuiFlow = MutableSharedFlow<EventLoopUpdateGui>()
    private val lastRunGuiFlow = MutableSharedFlow<EventLoopSetLastRunGui>()

    private val rh = FakeTextResources()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(rxBus.toFlow(EventLoopUpdateGui::class)).thenReturn(updateGuiFlow)
        whenever(rxBus.toFlow(EventLoopSetLastRunGui::class)).thenReturn(lastRunGuiFlow)
        whenever(dateUtil.dateAndTimeString(any())).thenReturn("date-and-time")
        whenever(dateUtil.dateAndTimeAndSecondsString(any())).thenReturn("date-time-seconds")
        // Locale independent, so the assertions below are stable on any machine.
        whenever(decimalFormatter.to2Decimal(any())).thenAnswer { invocation ->
            val text = (invocation.arguments[0] as Double).toString()
            if (text.endsWith(".0")) text.dropLast(2) else text
        }
    }

    private fun viewModel() =
        LoopViewModel(loop, rxBus, rh, dateUtil, decimalFormatter, aapsLogger, preferences, CoroutineScope(Dispatchers.Unconfined))

    private fun lastRunWith(tbr: PumpEnactResult? = null, smb: PumpEnactResult? = null): Loop.LastRun =
        Loop.LastRun().apply {
            source = "OpenAPS SMB"
            tbrSetByPump = tbr
            smbSetByPump = smb
        }

    // ---------------------------------------------------------------- toPlainText branches

    @Test
    fun tbrSetByPump_queued_showsOnlyWaitingText() {
        whenever(loop.lastRun).thenReturn(lastRunWith(tbr = FakePumpEnactResult(success = true, queued = true)))

        val sut = viewModel()

        // The queued branch overwrites the success line rather than appending to it.
        assertThat(sut.uiState.value.tbrSetByPump).isEqualTo("Waiting for result")
    }

    @Test
    fun tbrSetByPump_notEnacted_withComment_showsSuccessAndComment() {
        whenever(loop.lastRun).thenReturn(
            lastRunWith(tbr = FakePumpEnactResult(success = false, enacted = false, comment = "pump busy"))
        )

        val sut = viewModel()

        assertThat(sut.uiState.value.tbrSetByPump).isEqualTo("Success: false\nComment: pump busy")
    }

    @Test
    fun tbrSetByPump_notEnacted_withoutComment_showsOnlySuccessLine() {
        whenever(loop.lastRun).thenReturn(
            lastRunWith(tbr = FakePumpEnactResult(success = true, enacted = false, comment = ""))
        )

        val sut = viewModel()

        assertThat(sut.uiState.value.tbrSetByPump).isEqualTo("Success: true")
    }

    @Test
    fun smbSetByPump_bolusDelivered_showsBolusLineWithUnit() {
        whenever(loop.lastRun).thenReturn(
            lastRunWith(
                smb = FakePumpEnactResult(success = true, enacted = true, comment = "delivering", bolusDelivered = 0.3)
            )
        )

        val sut = viewModel()

        assertThat(sut.uiState.value.smbSetByPump)
            .isEqualTo("Success: true\nEnacted: true\nComment: delivering\nSMB: 0.3 U")
    }

    @Test
    fun smbSetByPump_bolusDelivered_withoutComment_skipsCommentLine() {
        whenever(loop.lastRun).thenReturn(
            lastRunWith(
                smb = FakePumpEnactResult(success = true, enacted = true, comment = "", bolusDelivered = 0.3)
            )
        )

        val sut = viewModel()

        assertThat(sut.uiState.value.smbSetByPump).isEqualTo("Success: true\nEnacted: true\nSMB: 0.3 U")
    }

    @Test
    fun tbrSetByPump_tempCancel_showsCancelLine_andAlwaysShowsComment() {
        whenever(loop.lastRun).thenReturn(
            lastRunWith(tbr = FakePumpEnactResult(success = true, enacted = true, comment = "", isTempCancel = true))
        )

        val sut = viewModel()

        // The cancel branch prints the comment line even when the comment is empty. That is what the
        // screen shows today, so it is what is pinned.
        assertThat(sut.uiState.value.tbrSetByPump).isEqualTo("Success: true\nEnacted: true\nComment: \nCancel temp")
    }

    @Test
    fun tbrSetByPump_percent_showsDurationAndPercent() {
        whenever(loop.lastRun).thenReturn(
            lastRunWith(
                tbr = FakePumpEnactResult(
                    success = true, enacted = true, comment = "ok", isPercent = true, percent = 150, duration = 30
                )
            )
        )

        val sut = viewModel()

        assertThat(sut.uiState.value.tbrSetByPump)
            .isEqualTo("Success: true\nEnacted: true\nComment: ok\nDuration: 30 min\nPercent: 150%")
    }

    @Test
    fun tbrSetByPump_percentMinusOne_fallsThroughToAbsoluteBranch() {
        // isPercent is true but percent is -1, so the percent branch is skipped and absolute wins.
        whenever(loop.lastRun).thenReturn(
            lastRunWith(
                tbr = FakePumpEnactResult(
                    success = true, enacted = true, comment = "ok", isPercent = true, percent = -1,
                    absolute = 1.25, duration = 30
                )
            )
        )

        val sut = viewModel()

        assertThat(sut.uiState.value.tbrSetByPump)
            .isEqualTo("Success: true\nEnacted: true\nComment: ok\nDuration: 30 min\nAbsolute: 1.25 U/h")
    }

    @Test
    fun tbrSetByPump_absolute_withoutComment_skipsCommentLine() {
        whenever(loop.lastRun).thenReturn(
            lastRunWith(
                tbr = FakePumpEnactResult(
                    success = true, enacted = true, comment = "", absolute = 0.85, duration = 45
                )
            )
        )

        val sut = viewModel()

        assertThat(sut.uiState.value.tbrSetByPump)
            .isEqualTo("Success: true\nEnacted: true\nDuration: 45 min\nAbsolute: 0.85 U/h")
    }

    @Test
    fun tbrSetByPump_enactedButNothingSet_showsOnlySuccessLine() {
        // enacted, not queued, no bolus, no cancel, not percent, absolute still -1.0: every inner
        // branch misses and only the first line survives - the comment is dropped.
        whenever(loop.lastRun).thenReturn(
            lastRunWith(tbr = FakePumpEnactResult(success = true, enacted = true, comment = "dropped"))
        )

        val sut = viewModel()

        assertThat(sut.uiState.value.tbrSetByPump).isEqualTo("Success: true")
    }

    // ---------------------------------------------------------------- updateState

    @Test
    fun constraints_showRateAndSmbBindingReasons_thenClosedLoopReasons() = runTest {
        // The Loop tab shows why the loop executed what it did: the binding reason from the rate and
        // smb constraints, then the closed loop reasons. Until 2026 this section silently showed only
        // the closed loop part - the view model collected the rate and smb reasons with copyReasons,
        // which fills `reasons`, but read them back with getMostLimitedReasons, which returns
        // `mostLimiting`. That list was never written, so the result was always empty.
        val processed = mock<APSResult> {
            whenever(it.rateConstraint).thenReturn(FakeConstraint(0.0, "rate limited"))
            whenever(it.smbConstraint).thenReturn(FakeConstraint(0.0, "smb limited"))
        }
        whenever(processed.resultAsString()).thenReturn("processed text")
        whenever(loop.lastRun).thenReturn(Loop.LastRun().apply { constraintsProcessed = processed })
        whenever(loop.closedLoopEnabled).thenReturn(FakeConstraint(false, "loop disabled"))

        val sut = viewModel()

        assertThat(sut.uiState.value.constraints).isEqualTo("rate limited\nsmb limited\nloop disabled")
        assertThat(sut.uiState.value.constraintsProcessed).isEqualTo("processed text")
    }

    @Test
    fun constraints_isEmpty_whenNoProcessedResultAndNoClosedLoopConstraint() {
        whenever(loop.lastRun).thenReturn(Loop.LastRun())
        whenever(loop.closedLoopEnabled).thenReturn(null)

        val sut = viewModel()

        assertThat(sut.uiState.value.constraints).isEmpty()
        assertThat(sut.uiState.value.constraintsProcessed).isEmpty()
    }

    @Test
    fun updateState_mapsDatesAndSource_andClearsRefreshing() {
        whenever(loop.lastRun).thenReturn(lastRunWith())

        val sut = viewModel()

        assertThat(sut.uiState.value.lastRun).isEqualTo("date-and-time")
        assertThat(sut.uiState.value.source).isEqualTo("OpenAPS SMB")
        assertThat(sut.uiState.value.tbrRequestTime).isEqualTo("date-time-seconds")
        assertThat(sut.uiState.value.smbExecutionTime).isEqualTo("date-time-seconds")
        assertThat(sut.uiState.value.isRefreshing).isFalse()
        assertThat(sut.uiState.value.statusMessage).isEmpty()
    }

    @Test
    fun nullSource_becomesEmptyString_notTheWordNull() {
        whenever(loop.lastRun).thenReturn(Loop.LastRun().apply { source = null })

        val sut = viewModel()

        assertThat(sut.uiState.value.source).isEmpty()
        assertThat(sut.uiState.value.tbrSetByPump).isEmpty()
        assertThat(sut.uiState.value.smbSetByPump).isEmpty()
    }

    // ---------------------------------------------------------------- init and events

    @Test
    fun init_marksObjectivesLoopUsed() {
        whenever(loop.lastRun).thenReturn(null)

        viewModel()

        verify(preferences).put(BooleanNonKey.ObjectivesLoopUsed, true)
    }

    @Test
    fun setLastRunGuiEvent_replacesWholeStateWithStatusMessage() = runTest {
        whenever(loop.lastRun).thenReturn(lastRunWith())

        val sut = viewModel()
        assertThat(sut.uiState.value.lastRun).isEqualTo("date-and-time")

        lastRunGuiFlow.emit(EventLoopSetLastRunGui("running now"))

        assertThat(sut.uiState.value.statusMessage).isEqualTo("running now")
        // The event replaces the state rather than merging into it, so the old run is cleared.
        assertThat(sut.uiState.value.lastRun).isEmpty()
    }

    @Test
    fun updateGuiEvent_recomputesStateFromLoop() = runTest {
        whenever(loop.lastRun).thenReturn(null)

        val sut = viewModel()
        assertThat(sut.uiState.value.statusMessage).isEqualTo("Not available")

        whenever(loop.lastRun).thenReturn(lastRunWith())
        updateGuiFlow.emit(EventLoopUpdateGui())

        assertThat(sut.uiState.value.lastRun).isEqualTo("date-and-time")
        assertThat(sut.uiState.value.statusMessage).isEmpty()
    }

    // ---------------------------------------------------------------- doubles

    /**
     * Answers by resource id and by [TextRef] name with the same word, so an assertion above holds
     * whichever of the two overloads the view model uses.
     */
    private class FakeTextResources : ResourceHelper {

        private val words = listOf(
            app.aaps.core.ui.R.string.success to ("success" to "Success"),
            app.aaps.core.ui.R.string.waitingforpumpresult to ("waitingforpumpresult" to "Waiting for result"),
            app.aaps.core.ui.R.string.enacted to ("enacted" to "Enacted"),
            app.aaps.core.ui.R.string.comment to ("comment" to "Comment"),
            app.aaps.core.ui.R.string.smb_shortname to ("smb_shortname" to "SMB"),
            app.aaps.core.ui.R.string.insulin_unit_shortname to ("insulin_unit_shortname" to "U"),
            app.aaps.core.ui.R.string.cancel_temp to ("cancel_temp" to "Cancel temp"),
            app.aaps.core.ui.R.string.duration to ("duration" to "Duration"),
            app.aaps.core.ui.R.string.percent to ("percent" to "Percent"),
            app.aaps.core.ui.R.string.absolute to ("absolute" to "Absolute"),
            app.aaps.core.ui.R.string.not_available_full to ("not_available_full" to "Not available")
        )

        private val byId = words.associate { it.first to it.second.second }
        private val byName = words.associate { it.second.first to it.second.second }

        override fun gs(id: Int): String = byId[id] ?: error("unmapped resource id $id")
        override fun gs(id: Int, vararg args: Any?): String = gs(id)
        override fun gq(id: Int, quantity: Int, vararg args: Any?): String = gs(id)
        override fun gsNotLocalised(id: Int, vararg args: Any?): String = gs(id)
        override fun shortTextMode(): Boolean = false

        override fun gs(ref: TextRef): String = when (ref) {
            is TextRef.Literal    -> ref.text
            is TextRef.AndroidRes -> gs(ref.id)
            is TextRef.Named      -> byName[ref.name] ?: error("unmapped string name ${ref.name}")
        }

        override fun gs(ref: TextRef, vararg args: Any?): String = gs(ref)
        override fun gsNotLocalised(ref: TextRef): String = gs(ref)
    }

    private class FakeConstraint<T : Comparable<T>>(
        private val value: T,
        private val reasons: String
    ) : Constraint<T> {

        override fun value(): T = value
        override fun originalValue(): T = value
        override fun set(value: T): Constraint<T> = this
        override fun set(value: T, reason: String, from: Any): Constraint<T> = this
        override fun setIfDifferent(value: T, reason: String, from: Any): Constraint<T> = this
        override fun setIfSmaller(value: T, reason: String, from: Any): Constraint<T> = this
        override fun setIfGreater(value: T, reason: String, from: Any): Constraint<T> = this
        override fun addReason(reason: String, from: Any) {}
        override fun getReasons(): String = reasons
        override val reasonList: List<String> get() = listOf(reasons)
        override fun getMostLimitedReasons(): String = reasons
        override val mostLimitedReasonList: List<String> get() = listOf(reasons)
        override fun copyReasons(another: Constraint<*>) {}
    }

    private class FakePumpEnactResult(
        override var success: Boolean = false,
        override var enacted: Boolean = false,
        override var comment: String = "",
        override var duration: Int = -1,
        override var absolute: Double = -1.0,
        override var percent: Int = -1,
        override var isPercent: Boolean = false,
        override var isTempCancel: Boolean = false,
        override var bolusDelivered: Double = 0.0,
        override var queued: Boolean = false
    ) : PumpEnactResult {

        override fun success(success: Boolean) = apply { this.success = success }
        override fun enacted(enacted: Boolean) = apply { this.enacted = enacted }
        override fun comment(comment: String) = apply { this.comment = comment }
        override fun comment(ref: TextRef) = this
        override fun duration(duration: Int) = apply { this.duration = duration }
        override fun absolute(absolute: Double) = apply { this.absolute = absolute }
        override fun percent(percent: Int) = apply { this.percent = percent }
        override fun isPercent(isPercent: Boolean) = apply { this.isPercent = isPercent }
        override fun isTempCancel(isTempCancel: Boolean) = apply { this.isTempCancel = isTempCancel }
        override fun bolusDelivered(bolusDelivered: Double) = apply { this.bolusDelivered = bolusDelivered }
        override fun queued(queued: Boolean) = apply { this.queued = queued }
    }
}
