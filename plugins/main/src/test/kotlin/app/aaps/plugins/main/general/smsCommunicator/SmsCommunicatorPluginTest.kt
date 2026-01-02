package app.aaps.plugins.main.general.smsCommunicator

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
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.fromGv
import app.aaps.plugins.aps.loop.LoopPlugin
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.smsCommunicator.otp.OneTimePassword
import app.aaps.plugins.main.general.smsCommunicator.otp.OneTimePasswordValidationResult
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import javax.inject.Provider

@Suppress("SpellCheckingInspection")
class SmsCommunicatorPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var loop: LoopPlugin
    @Mock lateinit var profileSource: ProfileSource
    @Mock lateinit var otp: OneTimePassword
    @Mock lateinit var xDripBroadcast: XDripBroadcast
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtilMocked: DateUtil
    @Mock lateinit var autosensDataStore: AutosensDataStore
    @Mock lateinit var smsManager: SmsManager
    @Mock lateinit var configBuilder: ConfigBuilder
    @Mock lateinit var pumpStatusProvider: PumpStatusProvider

    private lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    private val modeClosed = "Closed Loop"
    private val modeOpen = "Open Loop"
    private val modeLgs = "Low Glucose Suspend"
    private val modeUnknown = "unknown"

    @BeforeEach fun prepareTests() {
        val reading = GV(raw = 0.0, noise = 0.0, value = 100.0, timestamp = 1514766900000, sourceSensor = SourceSensor.UNKNOWN, trendArrow = TrendArrow.FLAT)
        val bgList: MutableList<GV> = ArrayList()
        bgList.add(reading)

        whenever(iobCobCalculator.getCobInfo("SMS COB")).thenReturn(CobInfo(0, 10.0, 2.0))
        whenever(iobCobCalculator.ads).thenReturn(autosensDataStore)
        whenever(autosensDataStore.lastBg()).thenReturn(InMemoryGlucoseValue.fromGv(reading))

        whenever(preferences.get(StringKey.SmsAllowedNumbers)).thenReturn("1234;5678")

        whenever(
            persistenceLayer.insertAndCancelCurrentTemporaryTarget(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(Single.just(PersistenceLayer.TransactionResult<TT>().apply {
        }))

        val authRequestProvider = Provider { AuthRequest(aapsLogger, smsCommunicatorPlugin, rh, otp, dateUtil, commandQueue) }
        smsCommunicatorPlugin = SmsCommunicatorPlugin(
            aapsLogger, rh, smsManager, aapsSchedulers, preferences, constraintChecker, rxBus, profileFunction, profileUtil, fabricPrivacy, activePlugin, commandQueue,
            loop, iobCobCalculator, xDripBroadcast,
            otp, config, dateUtilMocked, uel,
            smbGlucoseStatusProvider, persistenceLayer, decimalFormatter, configBuilder, authRequestProvider, pumpStatusProvider
        )
        smsCommunicatorPlugin.setPluginEnabledBlocking(PluginType.GENERAL, true)
        doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(2)
            callback.result = pumpEnactResultProvider.get().success(true)
            callback.run()
            null
        }.whenever(commandQueue).cancelTempBasal(anyBoolean(), anyBoolean(), any(Callback::class.java))
        doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(0)
            callback.result = pumpEnactResultProvider.get().success(true)
            callback.run()
            null
        }.whenever(commandQueue).cancelExtended(any(Callback::class.java))
        doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(1)
            callback.result = pumpEnactResultProvider.get().success(true)
            callback.run()
            null
        }.whenever(commandQueue).readStatus(anyString(), any(Callback::class.java))
        doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(1)
            callback.result = pumpEnactResultProvider.get().success(true).bolusDelivered(1.0)
            callback.run()
            null
        }.whenever(commandQueue).bolus(anyOrNull(), any(Callback::class.java))
        doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(5)
            callback.result = pumpEnactResultProvider.get().success(true).isPercent(true).percent(invocation.getArgument(0)).duration(invocation.getArgument(1))
            callback.run()
            null
        }.whenever(commandQueue)
            .tempBasalPercent(anyInt(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull(), any(Callback::class.java))
        doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(5)
            callback.result = pumpEnactResultProvider.get().success(true).isPercent(false).absolute(invocation.getArgument(0)).duration(invocation.getArgument(1))
            callback.run()
            null
        }.whenever(commandQueue)
            .tempBasalAbsolute(anyDouble(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull(), any(Callback::class.java))
        doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(2)
            callback.result = pumpEnactResultProvider.get().success(true).isPercent(false).absolute(invocation.getArgument(0)).duration(invocation.getArgument(1))
            callback.run()
            null
        }.whenever(commandQueue).extendedBolus(anyDouble(), anyInt(), any(Callback::class.java))

        whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(0))
        whenever(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(0))

        whenever(activePlugin.activeProfileSource).thenReturn(profileSource)
        whenever(profileFunction.getProfile()).thenReturn(validProfile)
        whenever(pumpStatusProvider.shortStatus(anyBoolean())).thenReturn(testPumpPlugin.pumpSpecificShortStatus(true))
        whenever(otp.name()).thenReturn("User")
        whenever(otp.checkOTP(anyString())).thenReturn(OneTimePasswordValidationResult.OK)

        whenever(rh.gs(R.string.smscommunicator_remote_command_not_allowed)).thenReturn("Remote command is not allowed")
        whenever(rh.gs(R.string.sms_wrong_code)).thenReturn("Wrong code. Command cancelled.")
        whenever(rh.gs(R.string.sms_iob)).thenReturn("IOB:")
        whenever(rh.gs(R.string.sms_last_bg)).thenReturn("Last BG:")
        whenever(rh.gs(R.string.sms_min_ago)).thenReturn("%1\$dmin ago")
        whenever(rh.gs(R.string.smscommunicator_remote_command_not_allowed)).thenReturn("Remote command is not allowed")
        whenever(rh.gs(R.string.smscommunicator_remote_command_not_possible)).thenReturn("Remote command is not possible")
        whenever(rh.gs(R.string.smscommunicator_stops_ns_with_code)).thenReturn("To disable the SMS Remote Service reply with code %1\$s.\\n\\nKeep in mind that you\\'ll able to reactivate it directly from the AAPS master smartphone only.")
        whenever(rh.gs(R.string.smscommunicator_meal_bolus_reply_with_code)).thenReturn("To deliver meal bolus %1$.2fU reply with code %2\$s.")
        whenever(rh.gs(R.string.smscommunicator_temptarget_with_code)).thenReturn("To set the Temp Target %1\$s reply with code %2\$s")
        whenever(rh.gs(R.string.smscommunicator_temptarget_cancel)).thenReturn("To cancel Temp Target reply with code %1\$s")
        whenever(rh.gs(R.string.smscommunicator_stopped_sms)).thenReturn("SMS Remote Service stopped. To reactivate it, use AAPS on master smartphone.")
        whenever(rh.gs(R.string.smscommunicator_tt_set)).thenReturn("Target %1\$s for %2\$d minutes set successfully")
        whenever(rh.gs(R.string.smscommunicator_tt_canceled)).thenReturn("Temp Target canceled successfully")
        whenever(rh.gs(R.string.sms_loop_suspended_for)).thenReturn("Suspended (%1\$d m)")
        whenever(rh.gs(app.aaps.core.ui.R.string.loopisdisabled)).thenReturn("Loop is disabled")
        whenever(rh.gs(R.string.smscommunicator_loop_is_enabled)).thenReturn("Loop is enabled")
        whenever(rh.gs(R.string.wrong_format)).thenReturn("Wrong format")
        whenever(rh.gs(eq(R.string.sms_wrong_tbr_duration), any())).thenAnswer { i: InvocationOnMock ->
            "TBR duration must be a multiple of " + i.arguments[1] + " minutes and greater than " +
                "0."
        }
        whenever(rh.gs(R.string.smscommunicator_loop_has_been_disabled)).thenReturn("Loop has been disabled")
        whenever(rh.gs(R.string.smscommunicator_tempbasal_canceled)).thenReturn("Temp basal canceled")
        whenever(rh.gs(R.string.smscommunicator_loop_resumed)).thenReturn("Loop resumed")
        whenever(rh.gs(R.string.smscommunicator_wrong_duration)).thenReturn("Wrong duration")
        whenever(rh.gs(R.string.smscommunicator_suspend_reply_with_code)).thenReturn("To suspend loop for %1\$d minutes reply with code %2\$s")
        whenever(rh.gs(R.string.smscommunicator_loop_suspended)).thenReturn("Loop suspended")
        whenever(rh.gs(R.string.smscommunicator_unknown_command)).thenReturn("Unknown command or wrong reply")
        whenever(rh.gs(app.aaps.core.ui.R.string.notconfigured)).thenReturn("Not configured")
        whenever(rh.gs(R.string.smscommunicator_profile_reply_with_code)).thenReturn("To switch profile to %1\$s %2\$d%% reply with code %3\$s")
        whenever(rh.gs(R.string.sms_profile_switch_created)).thenReturn("Profile switch created")
        whenever(rh.gs(R.string.smscommunicator_basal_stop_reply_with_code)).thenReturn("To stop temp basal reply with code %1\$s")
        whenever(rh.gs(R.string.smscommunicator_basal_pct_reply_with_code)).thenReturn("To start basal %1\$d%% for %2\$d min reply with code %3\$s")
        whenever(rh.gs(R.string.smscommunicator_tempbasal_set_percent)).thenReturn("Temp basal %1\$d%% for %2\$d min started successfully")
        whenever(rh.gs(R.string.smscommunicator_basal_reply_with_code)).thenReturn("To start basal %1$.2fU/h for %2\$d min reply with code %3\$s")
        whenever(rh.gs(R.string.smscommunicator_tempbasal_set)).thenReturn("Temp basal %1$.2fU/h for %2\$d min started successfully")
        whenever(rh.gs(R.string.smscommunicator_extended_stop_reply_with_code)).thenReturn("To stop extended bolus reply with code %1\$s")
        whenever(rh.gs(R.string.smscommunicator_extended_canceled)).thenReturn("Extended bolus canceled")
        whenever(rh.gs(R.string.smscommunicator_extended_reply_with_code)).thenReturn("To start extended bolus %1$.2fU for %2\$d min reply with code %3\$s")
        whenever(rh.gs(R.string.smscommunicator_extended_set)).thenReturn("Extended bolus %1$.2fU for %2\$d min started successfully")
        whenever(rh.gs(R.string.smscommunicator_bolus_reply_with_code)).thenReturn("To deliver bolus %1$.2fU reply with code %2\$s")
        whenever(rh.gs(R.string.smscommunicator_bolus_delivered)).thenReturn("Bolus %1$.2fU delivered successfully")
        whenever(rh.gs(R.string.smscommunicator_remote_bolus_not_allowed)).thenReturn("Remote bolus not available. Try again later.")
        whenever(rh.gs(R.string.smscommunicator_calibration_reply_with_code)).thenReturn("To send calibration %1$.2f reply with code %2\$s")
        whenever(rh.gs(R.string.smscommunicator_calibration_sent)).thenReturn("Calibration sent. Receiving must be enabled in xDrip.")
        whenever(rh.gs(R.string.smscommunicator_carbs_reply_with_code)).thenReturn("To enter %1\$dg at %2\$s reply with code %3\$s")
        whenever(rh.gs(R.string.smscommunicator_carbs_set)).thenReturn("Carbs %1\$dg entered successfully")
        whenever(rh.gs(app.aaps.core.ui.R.string.noprofile)).thenReturn("No profile loaded from NS yet")
        whenever(rh.gs(app.aaps.core.ui.R.string.pumpsuspended)).thenReturn("Pump suspended")
        whenever(rh.gs(app.aaps.core.interfaces.R.string.connected)).thenReturn("Connected")
        whenever(rh.gs(R.string.sms_delta)).thenReturn("Delta:")
        whenever(rh.gs(R.string.sms_bolus)).thenReturn("Bolus:")
        whenever(rh.gs(R.string.sms_basal)).thenReturn("Basal:")
        whenever(rh.gs(app.aaps.core.ui.R.string.cob)).thenReturn("COB")
        whenever(rh.gs(R.string.smscommunicator_meal_bolus_delivered)).thenReturn("Meal Bolus %1\$.2fU delivered successfully")
        whenever(rh.gs(R.string.smscommunicator_meal_bolus_delivered_tt)).thenReturn("Target %1\$s for %2\$d minutes")
        whenever(rh.gs(R.string.sms_actual_bg)).thenReturn("BG:")
        whenever(rh.gs(R.string.sms_last_bg)).thenReturn("Last BG:")
        whenever(rh.gs(R.string.smscommunicator_loop_disable_reply_with_code)).thenReturn("To disable loop reply with code %1\$s")
        whenever(rh.gs(R.string.smscommunicator_loop_resume_reply_with_code)).thenReturn("To resume loop reply with code %1\$s")
        whenever(rh.gs(R.string.smscommunicator_pump_disconnect_with_code)).thenReturn("To disconnect pump for %1d minutes reply with code %2\$s")
        whenever(rh.gs(R.string.smscommunicator_pump_connect_with_code)).thenReturn("To connect pump reply with code %1\$s")
        whenever(rh.gs(R.string.smscommunicator_reconnect)).thenReturn("Pump reconnected")
        whenever(rh.gs(R.string.smscommunicator_pump_connect_fail)).thenReturn("Connection to pump failed")
        whenever(rh.gs(R.string.smscommunicator_pump_disconnected)).thenReturn("Pump disconnected")
        whenever(rh.gs(R.string.smscommunicator_code_from_authenticator_for)).thenReturn("from Authenticator app for: %1\$s followed by PIN")
        whenever(rh.gs(app.aaps.core.ui.R.string.patient_name_default)).thenReturn("User")
        whenever(rh.gs(app.aaps.core.ui.R.string.invalid_profile)).thenReturn("Invalid profile !!!")
        whenever(rh.gs(app.aaps.core.ui.R.string.sms)).thenReturn("SMS")
        whenever(rh.gsNotLocalised(app.aaps.core.ui.R.string.loopsuspended)).thenReturn("Loop suspended")
        whenever(rh.gsNotLocalised(R.string.smscommunicator_stopped_sms)).thenReturn("SMS Remote Service stopped. To reactivate it, use AAPS on master smartphone.")
        whenever(rh.gsNotLocalised(R.string.sms_profile_switch_created)).thenReturn("Profile switch created")
        whenever(rh.gsNotLocalised(R.string.smscommunicator_tempbasal_canceled)).thenReturn("Temp basal canceled")
        whenever(rh.gsNotLocalised(R.string.smscommunicator_calibration_sent)).thenReturn("Calibration sent. Receiving must be enabled in xDrip+.")
        whenever(rh.gsNotLocalised(R.string.smscommunicator_tt_canceled)).thenReturn("Temp Target canceled successfully")
        whenever(rh.gs(app.aaps.core.ui.R.string.closedloop)).thenReturn(modeClosed)
        whenever(rh.gs(app.aaps.core.ui.R.string.openloop)).thenReturn(modeOpen)
        whenever(rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)).thenReturn(modeLgs)
        whenever(rh.gs(app.aaps.core.ui.R.string.unknown)).thenReturn(modeUnknown)
        whenever(rh.gs(R.string.smscommunicator_set_closed_loop_reply_with_code)).thenReturn("In order to switch Loop mode to Closed loop reply with code %1\$s")
        whenever(rh.gs(R.string.smscommunicator_current_loop_mode)).thenReturn("Current loop mode: %1\$s")
        whenever(rh.gs(R.string.smscommunicator_set_lgs_reply_with_code)).thenReturn("In order to switch Loop mode to LGS (Low Glucose Suspend) reply with code %1\$s")
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
        smsCommunicatorPlugin.messageToConfirm = AuthRequest(aapsLogger, smsCommunicatorPlugin, rh, otp, dateUtil, commandQueue).with(Sms("1234", "ddd"), "RequestText", "ccode", object : SmsAction(false) {
            override fun run() {}
        })
        assertThat(smsCommunicatorPlugin.isCommand("BLB", "1234")).isTrue()
        assertThat(smsCommunicatorPlugin.isCommand("BLB", "2345")).isFalse()
        smsCommunicatorPlugin.messageToConfirm = null
    }

    @Test fun isAllowedNumberTest() {
        assertThat(smsCommunicatorPlugin.isAllowedNumber("5678")).isTrue()
        assertThat(smsCommunicatorPlugin.isAllowedNumber("56")).isFalse()
    }

    @Test fun processSmsTest() {

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
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("IOB:")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("Last BG: 100")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("COB: 10(2)g")

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
        whenever(loop.runningMode).thenReturn(RM.Mode.DISABLED_LOOP)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Loop is disabled")

        //LOOP STATUS : suspended
        whenever(loop.minutesToEndOfSuspend()).thenReturn(10)
        whenever(loop.runningMode).thenReturn(RM.Mode.SUSPENDED_BY_USER)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Suspended (10 m)")

        //LOOP STATUS : enabled - APS mode - Closed
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Loop is enabled - $modeClosed")

        //LOOP STATUS : enabled - APS mode - Open
        whenever(loop.runningMode).thenReturn(RM.Mode.OPEN_LOOP)
        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("LOOP STATUS")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Loop is enabled - $modeOpen")

        //LOOP STATUS : enabled - APS mode - LGS
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP_LGS)
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
        whenever(persistenceLayer.cancelCurrentRunningMode(anyLong(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
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
        assertThat(smsCommunicatorPlugin.messages[1].text).contains(rh.gs(R.string.smscommunicator_remote_command_not_possible))

        //LOOP RESUME : already enabled
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Loop resumed")

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
        assertThat(smsCommunicatorPlugin.messages[1].text).contains(rh.gs(R.string.smscommunicator_remote_command_not_possible))

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
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Current loop mode: $modeClosed")
        // not allowed state
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", smsCommand)
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo(smsCommand)
        assertThat(smsCommunicatorPlugin.messages[1].text).contains(rh.gs(R.string.smscommunicator_remote_command_not_possible))

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
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Current loop mode: $modeLgs")
        // not allowed state
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", smsCommand)
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo(smsCommand)
        assertThat(smsCommunicatorPlugin.messages[1].text).contains(rh.gs(R.string.smscommunicator_remote_command_not_possible))

        //AAPSCLIENT RESTART
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "AAPSCLIENT RESTART")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("AAPSCLIENT RESTART")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("AAPSCLIENT RESTART")

        //AAPSCLIENT BLA BLA
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "AAPSCLIENT BLA BLA")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("AAPSCLIENT BLA BLA")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //AAPSCLIENT BLABLA
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP_LGS)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "AAPSCLIENT BLABLA")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(sms.ignored).isFalse()
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("AAPSCLIENT BLABLA")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

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
        whenever(profileFunction.getProfile()).thenReturn(validProfile)
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

        //PUMP DISCONNECT 30
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

        //RESTART
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "RESTART")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("RESTART")

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
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET MEAL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("TARGET MEAL")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To set the Temp Target")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).contains("set successfully")

        //TARGET STOP/CANCEL
        whenever(persistenceLayer.cancelCurrentTemporaryTargetIfAny(anyLong(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET STOP")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("TARGET STOP")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To cancel Temp Target reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).contains("Temp Target canceled successfully")
    }

    @Test fun processProfileTest() {

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
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE LIST")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE LIST")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Not configured")

        whenever(profileSource.profile).thenReturn(getValidProfileStore())
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

        //PROFILE 2 (non existing)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 2")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE 2")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        //PROFILE 1 0(wrong percentage)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1 0")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE 1 0")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

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

        //PROFILE 1 90(OK)
        whenever(
            profileFunction.createProfileSwitch(
                anyOrNull(),
                anyString(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyLong(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1 90")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("PROFILE 1 90")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To switch profile to someProfile 90% reply with code")
        val passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Profile switch created")
    }

    @Test fun processBasalTest() {

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

        whenever(profileFunction.getProfile()).thenReturn(validProfile)
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
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To start basal 1.00U/h for 30 min reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Temp basal 1.00U/h for 30 min started successfully\nVirtual Pump")
    }

    @Test fun processExtendedTest() {

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
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To start extended bolus 1.00U for 20 min reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Extended bolus 1.00U for 20 min started successfully\nVirtual Pump")
    }

    @Test fun processBolusTest() {

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
        whenever(dateUtilMocked.now()).thenReturn(Constants.remoteBolusMinDistance + 1002L)

        //BOLUS 0
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
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
        assertThat(smsCommunicatorPlugin.messages[3].text).contains("Bolus 1.00U delivered successfully")

        //BOLUS 1 (Suspended pump)
        smsCommunicatorPlugin.lastRemoteBolusTime = 0
        whenever(loop.runningMode).thenReturn(RM.Mode.SUSPENDED_BY_PUMP)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS 1")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Pump suspended")
        testPumpPlugin.pumpSuspended = false

        //BOLUS 1 a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1 a")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS 1 a")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("Wrong format")

        whenever(profileFunction.getProfile()).thenReturn(validProfile)
        whenever(preferences.get(UnitDoubleKey.OverviewEatingSoonTarget)).thenReturn(5.0)
        whenever(preferences.get(IntKey.OverviewEatingSoonDuration)).thenReturn(45)
        //BOLUS 1 MEAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1 MEAL")
        smsCommunicatorPlugin.processSms(sms)
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("BOLUS 1 MEAL")
        assertThat(smsCommunicatorPlugin.messages[1].text).contains("To deliver meal bolus 1.00U reply with code")
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        assertThat(smsCommunicatorPlugin.messages[2].text).isEqualTo(passCode)
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Meal Bolus 1.00U delivered successfully\nVirtual Pump\nTarget 5.0 for 45 minutes")
    }

    @Test fun processCalTest() {

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
        assertThat(smsCommunicatorPlugin.messages[3].text).isEqualTo("Calibration sent. Receiving must be enabled in xDrip.")
    }

    @Test fun processCarbsTest() {
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
        assertThat(smsCommunicatorPlugin.messages[3].text).startsWith("Carbs 1g entered successfully")

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
        assertThat(smsCommunicatorPlugin.messages[3].text).startsWith("Carbs 1g entered successfully")

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
        assertThat(smsCommunicatorPlugin.messages[3].text).startsWith("Carbs 1g entered successfully")
    }

    @Test fun sendNotificationToAllNumbers() {
        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.sendNotificationToAllNumbers("abc")
        assertThat(smsCommunicatorPlugin.messages[0].text).isEqualTo("abc")
        assertThat(smsCommunicatorPlugin.messages[1].text).isEqualTo("abc")
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        smsCommunicatorPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
