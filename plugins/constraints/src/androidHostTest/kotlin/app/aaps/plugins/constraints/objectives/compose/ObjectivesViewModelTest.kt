package app.aaps.plugins.constraints.objectives.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.constraints.ConstraintsStrings
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.SntpClient
import app.aaps.plugins.constraints.objectives.events.EventObjectivesUpdateGui
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.objectives.objectives.Objective.ExamTask
import app.aaps.plugins.constraints.objectives.objectives.Objective.Option
import app.aaps.plugins.constraints.objectives.objectives.Objective.UITask
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ObjectivesViewModelTest {

    private val objectivesPlugin: ObjectivesPlugin = mock()
    private val sntpClient: SntpClient = mock()
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var preferences: Preferences

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sut: ObjectivesViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // scope.launch { updateState() } + the update timer are deferred by StandardTestDispatcher, so the
        // objectivesPlugin.objectives list is never enumerated at construction; only the init flow chains are built.
        Dispatchers.setMain(dispatcher)
        whenever(rxBus.toFlow(EventObjectivesUpdateGui::class)).thenReturn(emptyFlow())
        // The 8 completion-tracking prefs are observed (.drop(1).merge()) in init — each must be non-null.
        listOf(
            BooleanNonKey.ObjectivesBgIsAvailableInNs, BooleanNonKey.ObjectivesPumpStatusIsAvailableInNS,
            BooleanNonKey.ObjectivesProfileSwitchUsed, BooleanNonKey.ObjectivesDisconnectUsed,
            BooleanNonKey.ObjectivesReconnectUsed, BooleanNonKey.ObjectivesTempTargetUsed,
            BooleanNonKey.ObjectivesLoopUsed, BooleanNonKey.ObjectivesScaleUsed
        ).forEach { whenever(preferences.observe(it)).thenReturn(MutableStateFlow(false)) }
        sut = ObjectivesViewModel(
            objectivesPlugin, rxBus, rh, dateUtil, sntpClient, receiverStatusStore, aapsLogger, uel, preferences
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is empty and not in fake mode`() {
        val state = sut.uiState.value
        assertThat(state.objectives).isEmpty()
        assertThat(state.isFakeMode).isFalse()
        assertThat(state.confirmUnstartDialog).isNull()
        assertThat(sut.scrollToIndex.value).isEqualTo(-1)
    }

    @Test
    fun `onFakeModeToggle flips fake mode`() {
        sut.onFakeModeToggle(true)
        assertThat(sut.uiState.value.isFakeMode).isTrue()

        sut.onFakeModeToggle(false)
        assertThat(sut.uiState.value.isFakeMode).isFalse()
    }

    @Test
    fun `unstart confirmation dialog is requested then dismissed`() {
        sut.onRequestUnstart(2)
        assertThat(sut.uiState.value.confirmUnstartDialog).isEqualTo(2)

        sut.onDismissUnstartDialog()
        assertThat(sut.uiState.value.confirmUnstartDialog).isNull()
    }

    /**
     * One objective, and text for every string, so the screen's own update can run along with the
     * time check. Every message the screen can show gets text of its own.
     */
    private fun oneObjective(): Objective {
        val objective: Objective = mock()
        whenever(objectivesPlugin.objectives).thenReturn(listOf(objective))
        whenever(rh.gs(anyOrNull<TextRef>())).thenReturn("text")
        whenever(rh.gs(anyOrNull<TextRef>(), anyVararg<Any>())).thenReturn("text")
        whenever(rh.gs(ConstraintsStrings.notconnected)).thenReturn(NOT_CONNECTED)
        whenever(rh.gs(ConstraintsStrings.failedretrievetime)).thenReturn(FAILED_RETRIEVE_TIME)
        whenever(rh.gs(ConstraintsStrings.requirementnotmet)).thenReturn(REQUIREMENT_NOT_MET)
        whenever(rh.gs(ConstraintsStrings.wronganswer)).thenReturn(WRONG_ANSWER)
        return objective
    }

    private fun network(known: Boolean = true, connected: Boolean = true) {
        whenever(receiverStatusStore.isKnownNetworkStatus).thenReturn(known)
        whenever(receiverStatusStore.isConnected).thenReturn(connected)
    }

    private fun timeServerAnswers(result: SntpClient.NtpResult) = runBlocking {
        whenever(sntpClient.ntpTime(any())).thenReturn(result)
    }

    /**
     * Runs what is due now. Never until idle: the screen refreshes itself every minute, so it is never
     * idle and a test that waited for that would hang.
     */
    private fun runDue() = dispatcher.scheduler.runCurrent()

    /**
     * A failed check must say so in the app-wide snackbar, as a warning or an error: those stay on
     * screen for 10 s and outlive the screen. The progress dialog must be gone too, so the message is
     * not hidden behind it.
     */
    private fun assertSays(message: String, type: EventShowSnackbar.Type) {
        verify(rxBus).send(EventShowSnackbar(message, type))
        assertThat(sut.uiState.value.ntpVerification).isNull()
    }

    private fun assertSaysNothing() = verify(rxBus, never()).send(any<EventShowSnackbar>())

    @Test
    fun `start with no network says not connected`() {
        val objective = oneObjective()
        network(connected = false)
        timeServerAnswers(SntpClient.NtpResult(success = false, networkConnected = false, time = 0))

        sut.onStart(0)
        runDue()

        assertSays(NOT_CONNECTED, EventShowSnackbar.Type.Warning)
        verify(objective, never()).startedOn = any()
    }

    /** The case reported as silent on Android: the network is up and the time server does not answer. */
    @Test
    fun `start with no answer from the time server says it failed`() {
        val objective = oneObjective()
        network()
        timeServerAnswers(SntpClient.NtpResult(success = false, networkConnected = true, time = 0))

        sut.onStart(0)
        runDue()

        assertSays(FAILED_RETRIEVE_TIME, EventShowSnackbar.Type.Error)
        verify(objective, never()).startedOn = any()
    }

    @Test
    fun `start with an unknown network asks nothing and says not connected`() {
        oneObjective()
        network(known = false)
        timeServerAnswers(SntpClient.NtpResult(success = false, networkConnected = false, time = 0))

        sut.onStart(0)
        runDue()

        assertSays(NOT_CONNECTED, EventShowSnackbar.Type.Warning)
        runBlocking { verify(sntpClient).ntpTime(false) }
    }

    @Test
    fun `start with the server time starts the objective at that time and says nothing`() {
        val objective = oneObjective()
        network()
        timeServerAnswers(SntpClient.NtpResult(success = true, networkConnected = true, time = SERVER_TIME))

        sut.onStart(0)
        runDue()

        verify(objective).startedOn = SERVER_TIME
        assertSaysNothing()
    }

    @Test
    fun `verify with no answer from the time server says it failed`() {
        oneObjective()
        network()
        timeServerAnswers(SntpClient.NtpResult(success = false, networkConnected = true, time = 0))

        sut.onVerify(0)
        runDue()

        assertSays(FAILED_RETRIEVE_TIME, EventShowSnackbar.Type.Error)
    }

    @Test
    fun `verify with no network says not connected`() {
        oneObjective()
        network(connected = false)
        timeServerAnswers(SntpClient.NtpResult(success = false, networkConnected = false, time = 0))

        sut.onVerify(0)
        runDue()

        assertSays(NOT_CONNECTED, EventShowSnackbar.Type.Warning)
    }

    @Test
    fun `verify too early by the server clock says the requirement is not met`() {
        val objective = oneObjective()
        network()
        timeServerAnswers(SntpClient.NtpResult(success = true, networkConnected = true, time = SERVER_TIME))
        runBlocking { whenever(objective.isCompleted(SERVER_TIME)).thenReturn(false) }

        sut.onVerify(0)
        runDue()

        assertSays(REQUIREMENT_NOT_MET, EventShowSnackbar.Type.Warning)
        verify(objective, never()).accomplishedOn = any()
    }

    @Test
    fun `a wrong exam answer says so`() {
        val objective = oneObjective()
        val task: ExamTask = mock()
        // One right option, left unticked: the answer is wrong.
        whenever(task.options).thenReturn(arrayListOf(Option(ConstraintsStrings.wronganswer, isCorrect = true)))
        whenever(objective.tasks).thenReturn(mutableListOf(task))
        runBlocking { whenever(objective.isCompleted()).thenReturn(false) }
        sut.onOpenExam(0, 0)
        runDue()

        sut.onExamVerify()

        verify(rxBus).send(EventShowSnackbar(WRONG_ANSWER, EventShowSnackbar.Type.Warning))
    }

    @Test
    fun `a task that cannot be done yet says why`() {
        val objective = oneObjective()
        val task: UITask = mock()
        whenever(task.code).thenReturn { _: UITask, _: Runnable, showMessage: (String) -> Unit -> showMessage(PASSWORD_NOT_SET) }
        whenever(objective.tasks).thenReturn(mutableListOf(task))

        sut.onInvokeUITask(0, 0)

        verify(rxBus).send(EventShowSnackbar(PASSWORD_NOT_SET, EventShowSnackbar.Type.Warning))
    }

    private companion object {

        const val NOT_CONNECTED = "Not connected to the internet"
        const val FAILED_RETRIEVE_TIME = "Failed to retrieve time"
        const val REQUIREMENT_NOT_MET = "Requirement not met"
        const val WRONG_ANSWER = "Wrong answer!"
        const val PASSWORD_NOT_SET = "Master password is not set"
        const val SERVER_TIME = 1_795_124_000_000L
    }
}
