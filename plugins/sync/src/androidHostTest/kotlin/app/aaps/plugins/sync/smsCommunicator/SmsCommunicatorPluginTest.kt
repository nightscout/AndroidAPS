package app.aaps.plugins.sync.smsCommunicator

import android.Manifest
import android.telephony.SmsManager
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.fromGv
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.plugins.aps.loop.LoopPlugin
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.SyncStringsValues
import app.aaps.plugins.sync.smsCommunicator.compose.SmsCommunicatorRepository
import app.aaps.plugins.sync.smsCommunicator.otp.OneTimePassword
import app.aaps.plugins.sync.smsCommunicator.otp.OneTimePasswordValidationResult
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

@Suppress("SpellCheckingInspection")
class SmsCommunicatorPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var loop: LoopPlugin
    @Mock lateinit var otp: OneTimePassword
    @Mock lateinit var xDripBroadcast: XDripBroadcast
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtilMocked: DateUtil
    @Mock lateinit var autosensDataStore: AutosensDataStore
    @Mock lateinit var smsManager: SmsManager
    @Mock lateinit var configBuilder: ConfigBuilder
    @Mock lateinit var pumpStatusProvider: PumpStatusProvider
    @Mock lateinit var bolusProgressData: BolusProgressData
    @Mock lateinit var wizardBolusExecutor: WizardBolusExecutor
    private lateinit var runningModeGuard: RunningModeGuard

    /**
     * Real English for every message this plugin sends, so the expected SMS text below is the text a
     * user would receive. `:shared:tests` cannot see this module, so the generated map is handed over
     * here rather than taking a new dependency.
     */
    private val text = generatedTextResolver("sync" to SyncStringsValues::textOf)

    private val repository = SmsCommunicatorRepository()
    private lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    private val modeClosed = "Closed Loop"
    private val modeOpen = "Open Loop"
    private val modeLgs = "Low Glucose Suspend"
    private val modeUnknown = "unknown"

    @BeforeEach fun prepareTests() {
        // A command reply quotes the enact result, which resolves its own comment. The base builds that
        // with the mocked `rh`, whose real default method answers a Named ref with its own NAME.
        pumpEnactResultProvider = { PumpEnactResultObject(text) }
        val reading = GV(raw = 0.0, noise = 0.0, value = 100.0, timestamp = 1514766900000, sourceSensor = SourceSensor.UNKNOWN, trendArrow = TrendArrow.FLAT)
        val bgList: MutableList<GV> = ArrayList()
        bgList.add(reading)

        runBlocking { whenever(iobCobCalculator.getCobInfo("SMS COB")).thenReturn(CobInfo(0, 10.0, 2.0)) }
        whenever(iobCobCalculator.ads).thenReturn(autosensDataStore)
        whenever(autosensDataStore.lastBg()).thenReturn(InMemoryGlucoseValue.fromGv(reading))

        whenever(preferences.get(StringKey.SmsAllowedNumbers)).thenReturn("1234;5678")

        runBlocking {
            whenever(
                persistenceLayer.insertAndCancelCurrentTemporaryTarget(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
            ).thenReturn(PersistenceLayer.TransactionResult())
        }
        // Use a real RunningModeGuard so the gate decisions actually fire from the mocked loop
        // (a mock guard returns null for everything → all gate-protected paths silently allow).
        runningModeGuard = RunningModeGuard(loop, text, rxBus)
        // Default running mode for tests that don't care; individual tests can override.
        // Without this, the gate sees null mode and PumpCommandGate.check throws NPE.
        runBlocking { whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP) }
        smsCommunicatorPlugin = SmsCommunicatorPlugin(
            aapsLogger, text, smsManager, preferences, constraintChecker, profileFunction, profileUtil, activePlugin, profileRepository,
            commandQueue, loop, iobCobCalculator, xDripBroadcast, otp, config, dateUtilMocked, uel,
            smbGlucoseStatusProvider, persistenceLayer, decimalFormatter, configBuilder, pumpStatusProvider, notificationManager,
            runningModeGuard, bolusProgressData, { wizardBolusExecutor }, repository
        )
        smsCommunicatorPlugin.setPluginEnabledBlocking(PluginType.SYNC, true)
        runBlocking {
            whenever(commandQueue.cancelTempBasal(anyBoolean(), anyBoolean())).thenReturn(pumpEnactResultProvider().success(true))
            whenever(commandQueue.cancelExtended()).thenReturn(pumpEnactResultProvider().success(true))
            whenever(commandQueue.readStatus(anyString())).thenReturn(pumpEnactResultProvider().success(true))
            whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true).bolusDelivered(1.0))
            whenever(commandQueue.tempBasalPercent(anyInt(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
                pumpEnactResultProvider().success(true).isPercent(true).percent(invocation.getArgument(0)).duration(invocation.getArgument(1))
            }
            whenever(commandQueue.tempBasalAbsolute(anyDouble(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
                pumpEnactResultProvider().success(true).isPercent(false).absolute(invocation.getArgument(0)).duration(invocation.getArgument(1))
            }
            whenever(commandQueue.extendedBolus(anyDouble(), anyInt())).thenAnswer { invocation ->
                pumpEnactResultProvider().success(true).isPercent(false).absolute(invocation.getArgument(0)).duration(invocation.getArgument(1))
            }
        }

        runBlocking { whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(0)) }
        runBlocking { whenever(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(0)) }

        runBlocking { whenever(profileFunction.getProfile()).thenReturn(effectiveProfile) }
        // A remote profile switch records the insulin in force; without one the SMS action refuses by design.
        runBlocking { whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(someICfg) }
        runBlocking { whenever(pumpStatusProvider.shortStatus(anyBoolean())).thenReturn(testPumpPlugin.pumpSpecificShortStatus(true)) }
        whenever(otp.name()).thenReturn("User")
        whenever(otp.checkOTP(anyString())).thenReturn(OneTimePasswordValidationResult.OK)

        // Fixed text, not an echo of the format argument: Mockito does not surface the varargs of the
        // `gs(TextRef, vararg Any?)` overload, and the assertions only check the fixed part anyway.
        // 30 is the durationStep the mocked pump reports; see the assertions in processBasalTest.
        // RunningModeGuard asks by TextRef, which is a different overload than the id above.
    }

    @Test
    fun processSettingsTest() {
        // called from constructor
        assertThat(smsCommunicatorPlugin.allowedNumbers[0]).isEqualTo("1234")
        assertThat(smsCommunicatorPlugin.allowedNumbers[1]).isEqualTo("5678")
        assertThat(smsCommunicatorPlugin.allowedNumbers).hasSize(2)
    }

    @Test
    fun isCommandTest() {
        assertThat(smsCommunicatorPlugin.isCommand("BOLUS", "")).isTrue()
        smsCommunicatorPlugin.messageToConfirm = null
        assertThat(smsCommunicatorPlugin.isCommand("BLB", "")).isFalse()
        smsCommunicatorPlugin.messageToConfirm = AuthRequest(
            requester = Sms("1234", "ddd"),
            requestText = "RequestText",
            confirmCode = "ccode",
            action = object : SmsAction(false) {
                override suspend fun run() {}
            },
            aapsLogger = aapsLogger,
            smsCommunicator = smsCommunicatorPlugin,
            rh = text,
            otp = otp,
            dateUtil = dateUtil,
            commandQueue = commandQueue
        )
        assertThat(smsCommunicatorPlugin.isCommand("BLB", "1234")).isTrue()
        assertThat(smsCommunicatorPlugin.isCommand("BLB", "2345")).isFalse()
        smsCommunicatorPlugin.messageToConfirm = null
    }

    @Test fun isAllowedNumberTest() {
        assertThat(smsCommunicatorPlugin.isAllowedNumber("5678")).isTrue()
        assertThat(smsCommunicatorPlugin.isAllowedNumber("56")).isFalse()
    }

    @Test fun processSmsTest() = runTest {

        // SMS from not allowed number should be ignored
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("12", "aText")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isTrue()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("aText")

        //UNKNOWN
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "UNKNOWN")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("UNKNOWN")

        //BG
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BG")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BG")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("IOB: 0.00U")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("Last BG: 100")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("COB: 10(2)g")
        assertThat(smsCommunicatorPlugin.messages[1].text).doesNotContain("Bolus:")
        assertThat(smsCommunicatorPlugin.messages[1].text).doesNotContain("Basal:")

        // LOOP : test remote control disabled
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("Remote command is not allowed")
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(true)

        //LOOP STATUS : disabled
        whenever(loop.runningMode()).thenReturn(RM.Mode.DISABLED_LOOP)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Loop is disabled")

        //LOOP STATUS : suspended
        whenever(loop.minutesToEndOfSuspend()).thenReturn(10)
        whenever(loop.runningMode()).thenReturn(RM.Mode.SUSPENDED_BY_USER)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Suspended (10 m)")

        //LOOP STATUS : enabled - APS mode - Closed
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Loop is enabled - $modeClosed")

        //LOOP STATUS : enabled - APS mode - Open
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Loop is enabled - $modeOpen")

        //LOOP STATUS : enabled - APS mode - LGS
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP_LGS)
        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Loop is enabled - $modeLgs")

        //LOOP : wrong format
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.RESUME))
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")
        whenever(persistenceLayer.cancelCurrentRunningMode(anyLong(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(PersistenceLayer.TransactionResult())
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP RESUME")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP RESUME")
        assertThat(smsCommunicatorPlugin.messages[1].text.contains("To resume loop reply with code ")).isTrue()
        // not allowed state
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP RESUME")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP RESUME")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains(text.gs(SyncStrings.smscommunicator_remote_command_not_possible))

        //LOOP RESUME : already enabled
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Loop resumed")
        // The reply above only proves an SMS went out. Pin down what actually reached the loop, so a
        // refactor of the execution path cannot change the mode, the audit action or the source silently.
        verify(loop).handleRunningModeChange(eq(RM.Mode.RESUME), eq(Action.RESUME), eq(Sources.SMS), anyOrNull(), eq(0), anyOrNull())

        //LOOP SUSPEND 1 2: wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 1 2")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP SUSPEND 1 2")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //LOOP SUSPEND 0 : wrong duration
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP SUSPEND 0")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong duration")

        //LOOP SUSPEND 100 : suspend for 100 min + correct answer
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.SUSPENDED_BY_USER))
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 100")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP SUSPEND 100")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To suspend loop for 100 minutes reply with code ")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Loop suspended Temp basal canceled")
        // The requested 100 minutes must arrive as 100 minutes.
        verify(loop).handleRunningModeChange(eq(RM.Mode.SUSPENDED_BY_USER), eq(Action.SUSPEND), eq(Sources.SMS), anyOrNull(), eq(100), anyOrNull())

        //LOOP SUSPEND 200 : limit to 180 min + wrong answer
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 200")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP SUSPEND 200")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To suspend loop for 180 minutes reply with code ")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        // ignore from other number
        smsCommunicatorPlugin.processSms(Sms("5678", passCode))
        whenever(otp.checkOTP(anyString())).thenReturn(OneTimePasswordValidationResult.ERROR_WRONG_OTP)
        smsCommunicatorPlugin.processSms(Sms("1234", "XXXX"))
        whenever(otp.checkOTP(anyString())).thenReturn(OneTimePasswordValidationResult.OK)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("XXXX")
        assertThat(smsCommunicatorPlugin.messages[4].text).isEqualTo("Wrong code. Command cancelled.")
        //then correct code should not work
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[5].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages).hasSize(6) // processed as common message

        // not allowed state
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 200")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP SUSPEND 200")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains(text.gs(SyncStrings.smscommunicator_remote_command_not_possible))

        //LOOP BLABLA
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP BLABLA")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP BLABLA")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //LOOP CLOSED
        var smsCommand = "LOOP CLOSED"
        val replyClosed = "In order to switch Loop mode to Closed loop reply with code "
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.CLOSED_LOOP))
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", smsCommand)
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo(smsCommand)
        assertThat(smsCommunicatorPlugin.messages[1].text).contains(replyClosed)
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Current running mode: $modeClosed")
        verify(loop).handleRunningModeChange(eq(RM.Mode.CLOSED_LOOP), eq(Action.CLOSED_LOOP_MODE), eq(Sources.SMS), anyOrNull(), eq(0), anyOrNull())
        // not allowed state
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", smsCommand)
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo(smsCommand)
        assertThat(smsCommunicatorPlugin.messages[1].text).contains(text.gs(SyncStrings.smscommunicator_remote_command_not_possible))

        //LOOP LGS
        smsCommand = "LOOP LGS"
        val replyLgs = "In order to switch Loop mode to LGS (Low Glucose Suspend) reply with code "
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.CLOSED_LOOP_LGS))
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", smsCommand)
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo(smsCommand)
        assertThat(smsCommunicatorPlugin.messages[1].text).contains(replyLgs)
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Current running mode: $modeLgs")
        verify(loop).handleRunningModeChange(eq(RM.Mode.CLOSED_LOOP_LGS), eq(Action.LGS_LOOP_MODE), eq(Sources.SMS), anyOrNull(), eq(0), anyOrNull())
        // not allowed state
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", smsCommand)
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo(smsCommand)
        assertThat(smsCommunicatorPlugin.messages[1].text).contains(text.gs(SyncStrings.smscommunicator_remote_command_not_possible))

        //PUMP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PUMP")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Virtual Pump")

        //PUMP CONNECT 1 2: wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP CONNECT 1 2")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PUMP CONNECT 1 2")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //PUMP CONNECT BLABLA
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP BLABLA")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PUMP BLABLA")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //PUMP CONNECT
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.RESUME))
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP CONNECT")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PUMP CONNECT")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To connect pump reply with code ")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Pump reconnected")
        // A reconnect is a RESUME carrying the RECONNECT audit action — not the same as LOOP RESUME above.
        verify(loop).handleRunningModeChange(eq(RM.Mode.RESUME), eq(Action.RECONNECT), eq(Sources.SMS), anyOrNull(), eq(0), anyOrNull())

        //PUMP DISCONNECT 1 2: wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 1 2")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PUMP DISCONNECT 1 2")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //PUMP DISCONNECT 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong duration")

        //PUMP DISCONNECT 30
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 30")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PUMP DISCONNECT 30")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To disconnect pump for")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Pump disconnected")
        verify(loop).handleRunningModeChange(eq(RM.Mode.DISCONNECTED_PUMP), eq(Action.DISCONNECT), eq(Sources.SMS), anyOrNull(), eq(30), anyOrNull())

        //PUMP DISCONNECT 200 : clamped to the 120 minute maximum
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 200")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PUMP DISCONNECT 200")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To disconnect pump for")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Pump disconnected")
        // The clamp is the point: 200 minutes requested, 120 reaches the loop. Only the reply prefix was
        // checked before, so the clamp could be lost without a single test turning red.
        verify(loop).handleRunningModeChange(eq(RM.Mode.DISCONNECTED_PUMP), eq(Action.DISCONNECT), eq(Sources.SMS), anyOrNull(), eq(120), anyOrNull())

        //RESTART - requires confirmation
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "RESTART")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("RESTART")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To restart AAPS reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("AAPS is restarting")

        //RESTART - wrong code
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "RESTART")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("RESTART")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To restart AAPS reply with code")
        whenever(otp.checkOTP(anyString())).thenReturn(OneTimePasswordValidationResult.ERROR_WRONG_OTP)
        smsCommunicatorPlugin.processSms(Sms("1234", "WRONG_CODE"))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo("WRONG_CODE")
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Wrong code. Command cancelled.")
        whenever(otp.checkOTP(anyString())).thenReturn(OneTimePasswordValidationResult.OK)

        //HELP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "HELP")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("HELP")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("PUMP")

        //HELP PUMP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "HELP PUMP")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("HELP PUMP")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("PUMP")

        //SMS : wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "SMS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("SMS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //SMS STOP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "SMS DISABLE")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("SMS DISABLE")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To disable the SMS Remote Service reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).contains("SMS Remote Service stopped. To reactivate it, use AAPS on master smartphone.")

        //TARGET : wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("TARGET")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //TARGET MEAL
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn(
            """[{"id":"eatingsoon","reason":"Eating Soon","targetValue":90.0,"duration":2700000,"isDeletable":false},{"id":"activity","reason":"Activity","targetValue":140.0,"duration":5400000,"isDeletable":false},{"id":"hypo","reason":"Hypo","targetValue":160.0,"duration":3600000,"isDeletable":false}]"""
        )
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET MEAL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("TARGET MEAL")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To set the Temp Target")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).contains("set successfully")
        // "set successfully" says nothing about WHAT was set. The eating-soon preset above is 90 mg/dL for
        // 45 minutes, so that is what has to be written — target, duration and reason all three.
        val mealTtCaptor = argumentCaptor<TT>()
        verify(persistenceLayer).insertAndCancelCurrentTemporaryTarget(mealTtCaptor.capture(), eq(Action.TT), eq(Sources.SMS), anyOrNull(), anyOrNull())
        assertThat(mealTtCaptor.firstValue.reason).isEqualTo(TT.Reason.EATING_SOON)
        assertThat(mealTtCaptor.firstValue.lowTarget).isEqualTo(90.0)
        assertThat(mealTtCaptor.firstValue.highTarget).isEqualTo(90.0)
        assertThat(mealTtCaptor.firstValue.duration).isEqualTo(2700000L) // 45 minutes

        //TARGET STOP/CANCEL
        whenever(persistenceLayer.cancelCurrentTemporaryTargetIfAny(anyLong(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(PersistenceLayer.TransactionResult())
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET STOP")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("TARGET STOP")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To cancel Temp Target reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).contains("Temp Target canceled successfully")
        verify(persistenceLayer).cancelCurrentTemporaryTargetIfAny(anyLong(), eq(Action.CANCEL_TT), eq(Sources.SMS), anyOrNull(), anyOrNull())
    }

    /**
     * LOOP SUSPEND clamps the duration to 180 minutes. `processSmsTest` sends 200 too, but that case ends
     * with a wrong confirmation code on purpose, so the action never runs and the clamp is only ever seen in
     * the reply text. This confirms the clamped value is what the loop is actually told.
     */
    @Test fun processLoopSuspendClampsDurationReachingTheLoop() = runTest {
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(true)
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.SUSPENDED_BY_USER))

        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.processSms(Sms("1234", "LOOP SUSPEND 200"))
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To suspend loop for 180 minutes reply with code ")
        val passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))

        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Loop suspended Temp basal canceled")
        verify(loop).handleRunningModeChange(eq(RM.Mode.SUSPENDED_BY_USER), eq(Action.SUSPEND), eq(Sources.SMS), anyOrNull(), eq(180), anyOrNull())
    }

    @Test fun processProfileTest() = runTest {

        //PROFILE
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "PROFILE")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Remote command is not allowed")
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(true)

        //PROFILE
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //PROFILE LIST (no profile defined)
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(null))
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE LIST")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE LIST")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Not configured")

        whenever(profileRepository.profile).thenReturn(MutableStateFlow(getValidProfileStore()))
        whenever(profileFunction.getProfileName()).thenReturn(TESTPROFILENAME)

        //PROFILE STATUS
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE STATUS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo(TESTPROFILENAME)

        //PROFILE LIST
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE LIST")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE LIST")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("1. $TESTPROFILENAME")

        //PROFILE 2 (non-existing)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 2")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE 2")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //PROFILE 1 0(wrong percentage) - 0 is outside the allowed range, so it is now reported as such
        // rather than as a generic "Wrong format". A non-numeric percentage still parses to 0 and lands here.
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE 1 0")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("»Profile-Percentage« is out of hard limits")

        //PROFILE 0(wrong index)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE 0")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //PROFILE 1(OK)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE 1")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To switch profile to someProfile 100% reply with code")

        //PROFILE 1 400 : out of range, refused before a pass code is ever asked for
        // (the previous case parked one, and a refused command does not clear it — start from nothing so
        // "no pass code was parked" is what the assertion actually proves)
        smsCommunicatorPlugin.messageToConfirm = null
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1 400")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE 1 400")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("»Profile-Percentage« is out of hard limits")
        assertThat(smsCommunicatorPlugin.messageToConfirm).isNull()

        //PROFILE 1 20 : below the range, same treatment
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1 20")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("»Profile-Percentage« is out of hard limits")
        assertThat(smsCommunicatorPlugin.messageToConfirm).isNull()

        //PROFILE 1 90(OK)
        whenever(wizardBolusExecutor.prepareBatch(anyOrNull())).thenReturn(WizardBolusExecutor.PrepareResult.Preview(insulin = 0.0, carbs = 0, bolusId = 42L))
        whenever(wizardBolusExecutor.confirm(anyLong(), anyOrNull(), anyOrNull(), anyBoolean(), anyDouble()))
            .thenReturn(WizardBolusExecutor.ConfirmResult.Delivered)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1 90")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE 1 90")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To switch profile to someProfile 90% reply with code")
        val passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Profile switch created")
        // The reply proves only that something succeeded. What matters now is the batch the SMS layer hands
        // to the executor: the right profile, the 90 the user sent, and no duration or time shift. What the
        // executor then does with it is covered by its own tests.
        val batchCaptor = argumentCaptor<List<BatchAction>>()
        verify(wizardBolusExecutor).prepareBatch(batchCaptor.capture())
        val switch = batchCaptor.firstValue.single() as BatchAction.ProfileSwitch
        assertThat(switch.profileName).isEqualTo(TESTPROFILENAME)
        assertThat(switch.percentage).isEqualTo(90)
        assertThat(switch.timeShiftHours).isEqualTo(0)
        assertThat(switch.durationMinutes).isEqualTo(0)
        // The parked batch is confirmed by its own id, and the switch is recorded as coming from SMS.
        verify(wizardBolusExecutor).confirm(eq(42L), eq(Sources.SMS), anyOrNull(), anyBoolean(), anyDouble())
    }

    /**
     * A remote switch has to record an insulin and must not invent one: the requester is not at the phone, so
     * with nothing in force the reply carries the reason and no switch is written.
     *
     * That rule now belongs to `WizardBolusExecutor.prepareBatch`, which refuses the batch, so the SMS side is
     * responsible for two things only: passing the refusal on as the reply, and not confirming anyway.
     */
    @Test fun processProfileWithNoInsulinInForceTest() = runTest {
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(true)
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(getValidProfileStore()))
        whenever(profileFunction.getProfileName()).thenReturn(TESTPROFILENAME)
        whenever(wizardBolusExecutor.prepareBatch(anyOrNull()))
            .thenReturn(WizardBolusExecutor.PrepareResult.Error("Cannot switch profile: no insulin is in use, and none was selected."))

        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.processSms(Sms("1234", "PROFILE 1 90"))
        val passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))

        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Cannot switch profile: no insulin is in use, and none was selected.")
        verifyBlocking(wizardBolusExecutor, never()) { confirm(anyLong(), anyOrNull(), anyOrNull(), anyBoolean(), anyDouble()) }
    }

    @Test fun processBasalTest() = runTest {

        //BASAL
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "BASAL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Remote command is not allowed")
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(true)

        //BASAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //BASAL CANCEL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL CANCEL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL CANCEL")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To stop temp basal reply with code")
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).contains("Temp basal canceled")

        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        //BASAL a%
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL a%")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL a%")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //BASAL 10% 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 10% 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL 10% 0")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("TBR duration must be a multiple of 30 minutes and greater than 0.")

        //BASAL 20% 20
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 20% 20")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL 20% 20")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("TBR duration must be a multiple of 30 minutes and greater than 0.")
        whenever(constraintChecker.applyBasalPercentConstraints(anyOrNull(), anyOrNull())).thenReturn(ConstraintObject(20, aapsLogger))

        //BASAL 20% 30
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 20% 30")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL 20% 30")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To start basal 20% for 30 min reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Temp basal 20% for 30 min started successfully\nVirtual Pump")

        //BASAL a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL a")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL a")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //BASAL 1 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 1 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL 1 0")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("TBR duration must be a multiple of 30 minutes and greater than 0.")
        whenever(constraintChecker.applyBasalConstraints(anyOrNull(), anyOrNull())).thenReturn(ConstraintObject(1.0, aapsLogger))

        //BASAL 1 20
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 1 20")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL 1 20")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("TBR duration must be a multiple of 30 minutes and greater than 0.")
        whenever(constraintChecker.applyBasalConstraints(anyOrNull(), anyOrNull())).thenReturn(ConstraintObject(1.0, aapsLogger))

        //BASAL 1 30
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 1 30")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BASAL 1 30")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To start basal 1.00 U/h for 30 min reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Temp basal 1.00U/h for 30 min started successfully\nVirtual Pump")
    }

    @Test fun processExtendedTest() = runTest {

        //EXTENDED
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "EXTENDED")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("EXTENDED")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Remote command is not allowed")
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(true)

        //EXTENDED
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("EXTENDED")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //EXTENDED CANCEL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED CANCEL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("EXTENDED CANCEL")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To stop extended bolus reply with code")
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).contains("Extended bolus canceled")

        //EXTENDED a%
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED a%")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("EXTENDED a%")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")
        whenever(constraintChecker.applyExtendedBolusConstraints(anyOrNull())).thenReturn(ConstraintObject(1.0, aapsLogger))

        //EXTENDED 1 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED 1 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("EXTENDED 1 0")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //EXTENDED 1 20
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED 1 20")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("EXTENDED 1 20")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To start extended bolus 1.00 U for 20 min reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Extended bolus 1.00U for 20 min started successfully\nVirtual Pump")
    }

    @Test fun processBolusTest() = runTest {

        //BOLUS
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "BOLUS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Remote command is not allowed")
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(true)

        //BOLUS
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")
        whenever(constraintChecker.applyBolusConstraints(anyOrNull())).thenReturn(ConstraintObject(1.0, aapsLogger))
        whenever(dateUtilMocked.now()).thenReturn(1000L)
        whenever(preferences.get(IntKey.SmsRemoteBolusDistance)).thenReturn(15)
        //BOLUS 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS 1")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Remote bolus not available. Try again later.")
        whenever(constraintChecker.applyBolusConstraints(anyOrNull())).thenReturn(ConstraintObject(0.0, aapsLogger))
        whenever(dateUtilMocked.now()).thenReturn(Constants.REMOTE_BOLUS_MIN_DISTANCE + 1002L)

        //BOLUS 0
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS 0")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //BOLUS a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS a")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS a")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")
        whenever(constraintChecker.applyExtendedBolusConstraints(anyOrNull())).thenReturn(ConstraintObject(1.0, aapsLogger))
        whenever(constraintChecker.applyBolusConstraints(anyOrNull())).thenReturn(ConstraintObject(1.0, aapsLogger))

        //BOLUS 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS 1")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To deliver bolus 1.00U reply with code")
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).contains("Bolus 1.00 U delivered successfully")
        // The "1.00 U" in that reply is the mocked queue's own canned answer, not the amount we asked for:
        // the stub returns bolusDelivered(1.0) whatever it is given. Read the amount off the real request.
        val bolusCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue).bolus(bolusCaptor.capture())
        assertThat(bolusCaptor.firstValue.insulin).isEqualTo(1.0)
        assertThat(bolusCaptor.firstValue.carbs).isEqualTo(0.0)

        //BOLUS 1 (Suspended pump)
        smsCommunicatorPlugin.lastRemoteBolusTime = 0
        whenever(loop.runningMode()).thenReturn(RM.Mode.SUSPENDED_BY_PUMP)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS 1")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Pump suspended")
        testPumpPlugin.pumpSuspended = false
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)

        //BOLUS 1 a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1 a")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS 1 a")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn(
            """[{"id":"eatingsoon","reason":"Eating Soon","targetValue":90.0,"duration":2700000,"isDeletable":false}]"""
        )
        //BOLUS 1 MEAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1 MEAL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS 1 MEAL")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To deliver meal bolus 1.00U reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Meal Bolus 1.00 U delivered successfully\nVirtual Pump\nTarget 5.0 for 45 minutes")
        // A meal bolus is a bolus PLUS an eating-soon temp target. Both halves are checked here, because the
        // order of the two is a behaviour the executor path would change.
        val mealBolusCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue, atLeastOnce()).bolus(mealBolusCaptor.capture())
        assertThat(mealBolusCaptor.lastValue.insulin).isEqualTo(1.0)
        val eatingSoonCaptor = argumentCaptor<TT>()
        verify(persistenceLayer).insertAndCancelCurrentTemporaryTarget(eatingSoonCaptor.capture(), eq(Action.TT), eq(Sources.SMS), anyOrNull(), anyOrNull())
        assertThat(eatingSoonCaptor.firstValue.reason).isEqualTo(TT.Reason.EATING_SOON)
        assertThat(eatingSoonCaptor.firstValue.lowTarget).isEqualTo(90.0)
        assertThat(eatingSoonCaptor.firstValue.duration).isEqualTo(2700000L) // 45 minutes

        //BOLUS 1 MEAL within the minimum remote-bolus distance must be rejected (meal form previously bypassed the spacing guard)
        smsCommunicatorPlugin.lastRemoteBolusTime = dateUtilMocked.now() - 100
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1 MEAL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS 1 MEAL")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Remote bolus not available. Try again later.")
    }

    @Test fun processBolusStopPressedTest() = runTest {
        // A bolus the user cancels mid-delivery still succeeds (partial), and the reply is prefixed with "STOP PRESSED".
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(true)
        whenever(constraintChecker.applyBolusConstraints(anyOrNull())).thenReturn(ConstraintObject(1.0, aapsLogger))
        whenever(constraintChecker.applyExtendedBolusConstraints(anyOrNull())).thenReturn(ConstraintObject(1.0, aapsLogger))
        whenever(preferences.get(IntKey.SmsRemoteBolusDistance)).thenReturn(15)
        whenever(dateUtilMocked.now()).thenReturn(Constants.REMOTE_BOLUS_MIN_DISTANCE + 1002L)
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(bolusProgressData.isStopPressed).thenReturn(true)
        smsCommunicatorPlugin.lastRemoteBolusTime = 0

        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.processSms(Sms("1234", "BOLUS 1"))
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To deliver bolus 1.00U reply with code")
        val passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[3].text).contains("STOP PRESSED Bolus 1.00 U delivered successfully")
    }

    @Test fun processCalTest() = runTest {

        //CAL
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "CAL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CAL")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Remote command is not allowed")
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(true)

        //CAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CAL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CAL")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //CAL 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CAL 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CAL 0")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")
        whenever(xDripBroadcast.sendCalibration(anyDouble())).thenReturn(true)
        //CAL 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CAL 1")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CAL 1")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To send calibration 1.00 reply with code")
        val passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Calibration sent. Receiving must be enabled in xDrip+.")
    }

    @Test fun processCarbsTest() = runTest {
        whenever(dateUtilMocked.now()).thenReturn(1000000L)
        whenever(dateUtilMocked.timeString(anyLong())).thenReturn("03:01AM")
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(false)
        //CAL
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "CARBS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CARBS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Remote command is not allowed")
        whenever(preferences.get(BooleanKey.SmsAllowRemoteCommands)).thenReturn(true)

        //CARBS
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CARBS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")
        whenever(constraintChecker.applyCarbsConstraints(anyOrNull())).thenReturn(ConstraintObject(0, aapsLogger))

        //CARBS 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CARBS 0")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")
        whenever(constraintChecker.applyCarbsConstraints(anyOrNull())).thenReturn(ConstraintObject(1, aapsLogger))

        //CARBS 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CARBS 1")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To enter 1g at")
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).startsWith("Carbs 1 g entered successfully")
        // Carbs travel as a bolus request with no insulin. With no time given they are stamped with
        // dateUtil.now(), which the mock reports as 0.
        val carbsCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue).bolus(carbsCaptor.capture())
        assertThat(carbsCaptor.firstValue.carbs).isEqualTo(1.0)
        assertThat(carbsCaptor.firstValue.insulin).isEqualTo(0.0)
        assertThat(carbsCaptor.firstValue.timestamp).isEqualTo(dateUtilMocked.now())

        //CARBS 1 a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 a")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CARBS 1 a")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("Wrong format")

        //CARBS 1 00
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 00")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CARBS 1 00")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("Wrong format")

        //CARBS 1 12:01
        whenever(dateUtilMocked.timeString(anyLong())).thenReturn("12:01PM")
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 12:01")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CARBS 1 12:01")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To enter 1g at 12:01PM reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).startsWith("Carbs 1 g entered successfully")
        // "12:01PM" in the reply is the mocked timeString, so it would read the same for any timestamp.
        // The parsed time has to be today at 12:01 local — the same wall clock the parser uses.
        val timedCarbsCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue, atLeastOnce()).bolus(timedCarbsCaptor.capture())
        assertThat(timedCarbsCaptor.lastValue.carbs).isEqualTo(1.0)
        assertThat(timedCarbsCaptor.lastValue.timestamp)
            .isEqualTo(DateTime().withHourOfDay(12).withMinuteOfHour(1).withSecondOfMinute(0).withMillisOfSecond(0).millis)

        //CARBS 1 3:01AM
        whenever(dateUtilMocked.timeString(anyLong())).thenReturn("03:01AM")
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 3:01AM")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("CARBS 1 3:01AM")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To enter 1g at 03:01AM reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).startsWith("Carbs 1 g entered successfully")
    }

    @Test fun sendNotificationToAllNumbers() = runTest {
        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.sendNotificationToAllNumbers("abc")
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("abc")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("abc")
    }

    @Test
    fun `requiredPermissions should include sms permissions`() {
        val allPermissions = smsCommunicatorPlugin.requiredPermissions().flatMap { it.permissions }
        assertThat(allPermissions).contains(Manifest.permission.RECEIVE_SMS)
        assertThat(allPermissions).contains(Manifest.permission.SEND_SMS)
        assertThat(allPermissions).contains(Manifest.permission.RECEIVE_MMS)
    }
}
