package info.nightscout.plugins.general.smsCommunicator

import android.telephony.SmsManager
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CancelCurrentOfflineEventIfAnyTransaction
import info.nightscout.database.impl.transactions.InsertAndCancelCurrentOfflineEventTransaction
import info.nightscout.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import info.nightscout.database.impl.transactions.Transaction
import info.nightscout.implementation.iob.GlucoseStatusProviderImpl
import info.nightscout.interfaces.ApsMode
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.iob.CobInfo
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.ProfileSource
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.smsCommunicator.Sms
import info.nightscout.plugins.R
import info.nightscout.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.plugins.general.smsCommunicator.otp.OneTimePasswordValidationResult
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.sharedtests.TestBaseWithProfile
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock

@Suppress("SpellCheckingInspection")
class SmsCommunicatorPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var loop: Loop
    @Mock lateinit var profileSource: ProfileSource
    @Mock lateinit var otp: OneTimePassword
    @Mock lateinit var xDripBroadcast: XDripBroadcast
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var dateUtilMocked: DateUtil
    @Mock lateinit var autosensDataStore: AutosensDataStore
    @Mock lateinit var smsManager: SmsManager

    private var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is PumpEnactResult) {
                it.context = context
            }
            if (it is AuthRequest) {
                it.aapsLogger = aapsLogger
                it.smsCommunicator = smsCommunicatorPlugin
                it.rh = rh
                it.otp = otp
                it.dateUtil = dateUtil
                it.commandQueue = commandQueue
            }
        }
    }

    private lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    private var hasBeenRun = false
    private val modeClosed = "Closed Loop"
    private val modeOpen = "Open Loop"
    private val modeLgs = "Low Glucose Suspend"
    private val modeUnknown = "unknown"

    @BeforeEach fun prepareTests() {
        val reading = GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = 1514766900000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT)
        val bgList: MutableList<GlucoseValue> = ArrayList()
        bgList.add(reading)

        `when`(iobCobCalculator.getCobInfo("SMS COB")).thenReturn(CobInfo(0, 10.0, 2.0))
        `when`(iobCobCalculator.ads).thenReturn(autosensDataStore)
        `when`(autosensDataStore.lastBg()).thenReturn(InMemoryGlucoseValue(reading))

        `when`(sp.getString(R.string.key_smscommunicator_allowednumbers, "")).thenReturn("1234;5678")

        `when`(
            repository.runTransactionForResult(anyObject<InsertAndCancelCurrentTemporaryTargetTransaction>())
        ).thenReturn(Single.just(InsertAndCancelCurrentTemporaryTargetTransaction.TransactionResult().apply {
        }))
        val glucoseStatusProvider = GlucoseStatusProviderImpl(aapsLogger = aapsLogger, iobCobCalculator = iobCobCalculator, dateUtil = dateUtilMocked)

        smsCommunicatorPlugin = SmsCommunicatorPlugin(
            injector, aapsLogger, rh, smsManager, aapsSchedulers, sp, constraintChecker, rxBus, profileFunction, fabricPrivacy, activePlugin, commandQueue,
            loop, iobCobCalculator, xDripBroadcast,
            otp, config, dateUtilMocked, uel,
            glucoseStatusProvider, repository
        )
        smsCommunicatorPlugin.setPluginEnabled(PluginType.GENERAL, true)
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(1)
            callback.result = PumpEnactResult(injector).success(true)
            callback.run()
            null
        }.`when`(commandQueue).cancelTempBasal(ArgumentMatchers.anyBoolean(), ArgumentMatchers.any(Callback::class.java))
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(0)
            callback.result = PumpEnactResult(injector).success(true)
            callback.run()
            null
        }.`when`(commandQueue).cancelExtended(ArgumentMatchers.any(Callback::class.java))
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(1)
            callback.result = PumpEnactResult(injector).success(true)
            callback.run()
            null
        }.`when`(commandQueue).readStatus(ArgumentMatchers.anyString(), ArgumentMatchers.any(Callback::class.java))
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(1)
            callback.result = PumpEnactResult(injector).success(true).bolusDelivered(1.0)
            callback.run()
            null
        }.`when`(commandQueue).bolus(anyObject(), ArgumentMatchers.any(Callback::class.java))
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(5)
            callback.result = PumpEnactResult(injector).success(true).isPercent(true).percent(invocation.getArgument(0)).duration(invocation.getArgument(1))
            callback.run()
            null
        }.`when`(commandQueue)
            .tempBasalPercent(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyBoolean(), anyObject(), anyObject(), ArgumentMatchers.any(Callback::class.java))
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(5)
            callback.result = PumpEnactResult(injector).success(true).isPercent(false).absolute(invocation.getArgument(0)).duration(invocation.getArgument(1))
            callback.run()
            null
        }.`when`(commandQueue)
            .tempBasalAbsolute(ArgumentMatchers.anyDouble(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyBoolean(), anyObject(), anyObject(), ArgumentMatchers.any(Callback::class.java))
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(2)
            callback.result = PumpEnactResult(injector).success(true).isPercent(false).absolute(invocation.getArgument(0)).duration(invocation.getArgument(1))
            callback.run()
            null
        }.`when`(commandQueue).extendedBolus(ArgumentMatchers.anyDouble(), ArgumentMatchers.anyInt(), ArgumentMatchers.any(Callback::class.java))

        `when`(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(0))
        `when`(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(0))

        `when`(activePlugin.activeProfileSource).thenReturn(profileSource)

        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)

        `when`(otp.name()).thenReturn("User")
        `when`(otp.checkOTP(ArgumentMatchers.anyString())).thenReturn(OneTimePasswordValidationResult.OK)

        `when`(rh.gs(R.string.smscommunicator_remote_command_not_allowed)).thenReturn("Remote command is not allowed")
        `when`(rh.gs(R.string.sms_wrong_code)).thenReturn("Wrong code. Command cancelled.")
        `when`(rh.gs(R.string.sms_iob)).thenReturn("IOB:")
        `when`(rh.gs(R.string.sms_last_bg)).thenReturn("Last BG:")
        `when`(rh.gs(R.string.sms_min_ago)).thenReturn("%1\$dmin ago")
        `when`(rh.gs(R.string.smscommunicator_remote_command_not_allowed)).thenReturn("Remote command is not allowed")
        `when`(rh.gs(R.string.smscommunicator_stops_ns_with_code)).thenReturn("To disable the SMS Remote Service reply with code %1\$s.\\n\\nKeep in mind that you\\'ll able to reactivate it directly from the AAPS master smartphone only.")
        `when`(rh.gs(R.string.smscommunicator_meal_bolus_reply_with_code)).thenReturn("To deliver meal bolus %1$.2fU reply with code %2\$s.")
        `when`(rh.gs(R.string.smscommunicator_temptarget_with_code)).thenReturn("To set the Temp Target %1\$s reply with code %2\$s")
        `when`(rh.gs(R.string.smscommunicator_temptarget_cancel)).thenReturn("To cancel Temp Target reply with code %1\$s")
        `when`(rh.gs(R.string.smscommunicator_stopped_sms)).thenReturn("SMS Remote Service stopped. To reactivate it, use AAPS on master smartphone.")
        `when`(rh.gs(R.string.smscommunicator_tt_set)).thenReturn("Target %1\$s for %2\$d minutes set successfully")
        `when`(rh.gs(R.string.smscommunicator_tt_canceled)).thenReturn("Temp Target canceled successfully")
        `when`(rh.gs(R.string.sms_loop_suspended_for)).thenReturn("Suspended (%1\$d m)")
        `when`(rh.gs(info.nightscout.core.ui.R.string.loopisdisabled)).thenReturn("Loop is disabled")
        `when`(rh.gs(R.string.smscommunicator_loop_is_enabled)).thenReturn("Loop is enabled")
        `when`(rh.gs(R.string.wrong_format)).thenReturn("Wrong format")
        `when`(rh.gs(eq(R.string.sms_wrong_tbr_duration), ArgumentMatchers.any())).thenAnswer { i: InvocationOnMock ->
            "TBR duration must be a multiple of " + i.arguments[1] + " minutes and greater than " +
                "0."
        }
        `when`(rh.gs(R.string.smscommunicator_loop_has_been_disabled)).thenReturn("Loop has been disabled")
        `when`(rh.gs(R.string.smscommunicator_loop_has_been_enabled)).thenReturn("Loop has been enabled")
        `when`(rh.gs(R.string.smscommunicator_tempbasal_canceled)).thenReturn("Temp basal canceled")
        `when`(rh.gs(R.string.smscommunicator_loop_resumed)).thenReturn("Loop resumed")
        `when`(rh.gs(R.string.smscommunicator_wrong_duration)).thenReturn("Wrong duration")
        `when`(rh.gs(R.string.smscommunicator_suspend_reply_with_code)).thenReturn("To suspend loop for %1\$d minutes reply with code %2\$s")
        `when`(rh.gs(R.string.smscommunicator_loop_suspended)).thenReturn("Loop suspended")
        `when`(rh.gs(R.string.smscommunicator_unknown_command)).thenReturn("Unknown command or wrong reply")
        `when`(rh.gs(info.nightscout.core.ui.R.string.notconfigured)).thenReturn("Not configured")
        `when`(rh.gs(R.string.smscommunicator_profile_reply_with_code)).thenReturn("To switch profile to %1\$s %2\$d%% reply with code %3\$s")
        `when`(rh.gs(R.string.sms_profile_switch_created)).thenReturn("Profile switch created")
        `when`(rh.gs(R.string.smscommunicator_basal_stop_reply_with_code)).thenReturn("To stop temp basal reply with code %1\$s")
        `when`(rh.gs(R.string.smscommunicator_basal_pct_reply_with_code)).thenReturn("To start basal %1\$d%% for %2\$d min reply with code %3\$s")
        `when`(rh.gs(R.string.smscommunicator_tempbasal_set_percent)).thenReturn("Temp basal %1\$d%% for %2\$d min started successfully")
        `when`(rh.gs(R.string.smscommunicator_basal_reply_with_code)).thenReturn("To start basal %1$.2fU/h for %2\$d min reply with code %3\$s")
        `when`(rh.gs(R.string.smscommunicator_tempbasal_set)).thenReturn("Temp basal %1$.2fU/h for %2\$d min started successfully")
        `when`(rh.gs(R.string.smscommunicator_extended_stop_reply_with_code)).thenReturn("To stop extended bolus reply with code %1\$s")
        `when`(rh.gs(R.string.smscommunicator_extended_canceled)).thenReturn("Extended bolus canceled")
        `when`(rh.gs(R.string.smscommunicator_extended_reply_with_code)).thenReturn("To start extended bolus %1$.2fU for %2\$d min reply with code %3\$s")
        `when`(rh.gs(R.string.smscommunicator_extended_set)).thenReturn("Extended bolus %1$.2fU for %2\$d min started successfully")
        `when`(rh.gs(R.string.smscommunicator_bolus_reply_with_code)).thenReturn("To deliver bolus %1$.2fU reply with code %2\$s")
        `when`(rh.gs(R.string.smscommunicator_bolus_delivered)).thenReturn("Bolus %1$.2fU delivered successfully")
        `when`(rh.gs(R.string.smscommunicator_remote_bolus_not_allowed)).thenReturn("Remote bolus not available. Try again later.")
        `when`(rh.gs(R.string.smscommunicator_calibration_reply_with_code)).thenReturn("To send calibration %1$.2f reply with code %2\$s")
        `when`(rh.gs(R.string.smscommunicator_calibration_sent)).thenReturn("Calibration sent. Receiving must be enabled in xDrip.")
        `when`(rh.gs(R.string.smscommunicator_carbs_reply_with_code)).thenReturn("To enter %1\$dg at %2\$s reply with code %3\$s")
        `when`(rh.gs(R.string.smscommunicator_carbs_set)).thenReturn("Carbs %1\$dg entered successfully")
        `when`(rh.gs(info.nightscout.core.ui.R.string.noprofile)).thenReturn("No profile loaded from NS yet")
        `when`(rh.gs(info.nightscout.core.ui.R.string.pumpsuspended)).thenReturn("Pump suspended")
        `when`(rh.gs(R.string.sms_delta)).thenReturn("Delta:")
        `when`(rh.gs(R.string.sms_bolus)).thenReturn("Bolus:")
        `when`(rh.gs(R.string.sms_basal)).thenReturn("Basal:")
        `when`(rh.gs(info.nightscout.core.ui.R.string.cob)).thenReturn("COB")
        `when`(rh.gs(R.string.smscommunicator_meal_bolus_delivered)).thenReturn("Meal Bolus %1\$.2fU delivered successfully")
        `when`(rh.gs(R.string.smscommunicator_meal_bolus_delivered_tt)).thenReturn("Target %1\$s for %2\$d minutes")
        `when`(rh.gs(R.string.sms_actual_bg)).thenReturn("BG:")
        `when`(rh.gs(R.string.sms_last_bg)).thenReturn("Last BG:")
        `when`(rh.gs(R.string.smscommunicator_loop_disable_reply_with_code)).thenReturn("To disable loop reply with code %1\$s")
        `when`(rh.gs(R.string.smscommunicator_loop_enable_reply_with_code)).thenReturn("To enable loop reply with code %1\$s")
        `when`(rh.gs(R.string.smscommunicator_loop_resume_reply_with_code)).thenReturn("To resume loop reply with code %1\$s")
        `when`(rh.gs(R.string.smscommunicator_pump_disconnect_with_code)).thenReturn("To disconnect pump for %1d minutes reply with code %2\$s")
        `when`(rh.gs(R.string.smscommunicator_pump_connect_with_code)).thenReturn("To connect pump reply with code %1\$s")
        `when`(rh.gs(R.string.smscommunicator_reconnect)).thenReturn("Pump reconnected")
        `when`(rh.gs(R.string.smscommunicator_pump_connect_fail)).thenReturn("Connection to pump failed")
        `when`(rh.gs(R.string.smscommunicator_pump_disconnected)).thenReturn("Pump disconnected")
        `when`(rh.gs(R.string.smscommunicator_code_from_authenticator_for)).thenReturn("from Authenticator app for: %1\$s followed by PIN")
        `when`(rh.gs(info.nightscout.core.ui.R.string.patient_name_default)).thenReturn("User")
        `when`(rh.gs(info.nightscout.core.ui.R.string.invalid_profile)).thenReturn("Invalid profile !!!")
        `when`(rh.gs(info.nightscout.core.ui.R.string.sms)).thenReturn("SMS")
        `when`(rh.gsNotLocalised(info.nightscout.core.ui.R.string.loopsuspended)).thenReturn("Loop suspended")
        `when`(rh.gsNotLocalised(R.string.smscommunicator_stopped_sms)).thenReturn("SMS Remote Service stopped. To reactivate it, use AAPS on master smartphone.")
        `when`(rh.gsNotLocalised(R.string.sms_profile_switch_created)).thenReturn("Profile switch created")
        `when`(rh.gsNotLocalised(R.string.smscommunicator_tempbasal_canceled)).thenReturn("Temp basal canceled")
        `when`(rh.gsNotLocalised(R.string.smscommunicator_calibration_sent)).thenReturn("Calibration sent. Receiving must be enabled in xDrip+.")
        `when`(rh.gsNotLocalised(R.string.smscommunicator_tt_canceled)).thenReturn("Temp Target canceled successfully")
        `when`(rh.gs(info.nightscout.core.ui.R.string.closedloop)).thenReturn(modeClosed)
        `when`(rh.gs(info.nightscout.core.ui.R.string.openloop)).thenReturn(modeOpen)
        `when`(rh.gs(info.nightscout.core.ui.R.string.lowglucosesuspend)).thenReturn(modeLgs)
        `when`(rh.gs(info.nightscout.core.ui.R.string.unknown)).thenReturn(modeUnknown)
        `when`(rh.gs(R.string.smscommunicator_set_closed_loop_reply_with_code)).thenReturn("In order to switch Loop mode to Closed loop reply with code %1\$s")
        `when`(rh.gs(R.string.smscommunicator_current_loop_mode)).thenReturn("Current loop mode: %1\$s")
        `when`(rh.gs(R.string.smscommunicator_set_lgs_reply_with_code)).thenReturn("In order to switch Loop mode to LGS (Low Glucose Suspend) reply with code %1\$s")
    }

    @Test
    fun processSettingsTest() {
        // called from constructor
        Assertions.assertEquals("1234", smsCommunicatorPlugin.allowedNumbers[0])
        Assertions.assertEquals("5678", smsCommunicatorPlugin.allowedNumbers[1])
        Assertions.assertEquals(2, smsCommunicatorPlugin.allowedNumbers.size)
    }

    @Test
    fun isCommandTest() {
        Assertions.assertTrue(smsCommunicatorPlugin.isCommand("BOLUS", ""))
        smsCommunicatorPlugin.messageToConfirm = null
        Assertions.assertFalse(smsCommunicatorPlugin.isCommand("BLB", ""))
        smsCommunicatorPlugin.messageToConfirm = AuthRequest(injector, Sms("1234", "ddd"), "RequestText", "ccode", object : SmsAction(false) {
            override fun run() {}
        })
        Assertions.assertTrue(smsCommunicatorPlugin.isCommand("BLB", "1234"))
        Assertions.assertFalse(smsCommunicatorPlugin.isCommand("BLB", "2345"))
        smsCommunicatorPlugin.messageToConfirm = null
    }

    @Test fun isAllowedNumberTest() {
        Assertions.assertTrue(smsCommunicatorPlugin.isAllowedNumber("5678"))
        Assertions.assertFalse(smsCommunicatorPlugin.isAllowedNumber("56"))
    }

    @Test fun processSmsTest() {

        // SMS from not allowed number should be ignored
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("12", "aText")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertTrue(sms.ignored)
        Assertions.assertEquals("aText", smsCommunicatorPlugin.messages[0].text)

        //UNKNOWN
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "UNKNOWN")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("UNKNOWN", smsCommunicatorPlugin.messages[0].text)

        //BG
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BG")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BG", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("IOB:"))
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("Last BG: 100"))
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("COB: 10(2)g"))

        // LOOP : test remote control disabled
        `when`(sp.getBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("Remote command is not allowed"))
        `when`(sp.getBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)).thenReturn(true)

        //LOOP STATUS : disabled
        `when`(loop.enabled).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Loop is disabled", smsCommunicatorPlugin.messages[1].text)

        //LOOP STATUS : suspended
        `when`(loop.minutesToEndOfSuspend()).thenReturn(10)
        `when`(loop.enabled).thenReturn(true)
        `when`(loop.isSuspended).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Suspended (10 m)", smsCommunicatorPlugin.messages[1].text)

        //LOOP STATUS : enabled - APS mode - Closed
        `when`(loop.enabled).thenReturn(true)
        `when`(loop.isSuspended).thenReturn(false)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.CLOSED.name)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Loop is enabled - $modeClosed", smsCommunicatorPlugin.messages[1].text)

        //LOOP STATUS : enabled - APS mode - Open
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.OPEN.name)
        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Loop is enabled - $modeOpen", smsCommunicatorPlugin.messages[1].text)

        //LOOP STATUS : enabled - APS mode - LGS
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.LGS.name)
        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Loop is enabled - $modeLgs", smsCommunicatorPlugin.messages[1].text)

        //LOOP STATUS : enabled - APS mode - unknown
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn("some wrong value")
        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Loop is enabled - $modeUnknown", smsCommunicatorPlugin.messages[1].text)

        //LOOP : wrong format
        `when`(loop.enabled).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //LOOP DISABLE : already disabled
        `when`(loop.enabled).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP DISABLE")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP DISABLE", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Loop is disabled", smsCommunicatorPlugin.messages[1].text)

        //LOOP DISABLE : from enabled
        hasBeenRun = false
        `when`(loop.enabled).thenReturn(true)
        // PowerMockito.doAnswer(Answer {
        //     hasBeenRun = true
        //     null
        // } as Answer<*>).`when`(loop).setPluginEnabled(PluginType.LOOP, false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP DISABLE")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP DISABLE", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To disable loop reply with code "))
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Loop has been disabled Temp basal canceled", smsCommunicatorPlugin.messages[3].text)
        //Assertions.assertTrue(hasBeenRun)

        //LOOP ENABLE : already enabled
        `when`(loop.enabled).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP ENABLE")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP ENABLE", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Loop is enabled", smsCommunicatorPlugin.messages[1].text)

        //LOOP ENABLE : from disabled
        hasBeenRun = false
        `when`(loop.enabled).thenReturn(false)
        // PowerMockito.doAnswer(Answer {
        //     hasBeenRun = true
        //     null
        // } as Answer<*>).`when`(loop).setPluginEnabled(PluginType.LOOP, true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP ENABLE")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP ENABLE", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To enable loop reply with code "))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Loop has been enabled", smsCommunicatorPlugin.messages[3].text)
        //Assertions.assertTrue(hasBeenRun)

        //LOOP RESUME : already enabled
        `when`(
            repository.runTransactionForResult(anyObject<Transaction<CancelCurrentOfflineEventIfAnyTransaction.TransactionResult>>())
        ).thenReturn(Single.just(CancelCurrentOfflineEventIfAnyTransaction.TransactionResult().apply {
        }))
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP RESUME")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP RESUME", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To resume loop reply with code "))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Loop resumed", smsCommunicatorPlugin.messages[3].text)

        //LOOP SUSPEND 1 2: wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 1 2")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP SUSPEND 1 2", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //LOOP SUSPEND 0 : wrong duration
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 0")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP SUSPEND 0", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong duration", smsCommunicatorPlugin.messages[1].text)

        //LOOP SUSPEND 100 : suspend for 100 min + correct answer
        `when`(
            repository.runTransactionForResult(anyObject<Transaction<InsertAndCancelCurrentOfflineEventTransaction.TransactionResult>>())
        ).thenReturn(Single.just(InsertAndCancelCurrentOfflineEventTransaction.TransactionResult().apply {
        }))
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 100")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP SUSPEND 100", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To suspend loop for 100 minutes reply with code "))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Loop suspended Temp basal canceled", smsCommunicatorPlugin.messages[3].text)

        //LOOP SUSPEND 200 : limit to 180 min + wrong answer
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 200")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP SUSPEND 200", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To suspend loop for 180 minutes reply with code "))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        // ignore from other number
        smsCommunicatorPlugin.processSms(Sms("5678", passCode))
        `when`(otp.checkOTP(ArgumentMatchers.anyString())).thenReturn(OneTimePasswordValidationResult.ERROR_WRONG_OTP)
        smsCommunicatorPlugin.processSms(Sms("1234", "XXXX"))
        `when`(otp.checkOTP(ArgumentMatchers.anyString())).thenReturn(OneTimePasswordValidationResult.OK)
        Assertions.assertEquals("XXXX", smsCommunicatorPlugin.messages[3].text)
        Assertions.assertEquals("Wrong code. Command cancelled.", smsCommunicatorPlugin.messages[4].text)
        //then correct code should not work
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[5].text)
        Assertions.assertEquals(6, smsCommunicatorPlugin.messages.size.toLong()) // processed as common message

        //LOOP BLABLA
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP BLABLA")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("LOOP BLABLA", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //LOOP CLOSED
        var smsCommand = "LOOP CLOSED"
        val replyClosed = "In order to switch Loop mode to Closed loop reply with code "
        `when`(loop.enabled).thenReturn(true)
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.CLOSED.name)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", smsCommand)
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals(smsCommand, smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains(replyClosed))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Current loop mode: $modeClosed", smsCommunicatorPlugin.messages[3].text)

        //LOOP LGS
        smsCommand = "LOOP LGS"
        val replyLgs = "In order to switch Loop mode to LGS (Low Glucose Suspend) reply with code "
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.LGS.name)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", smsCommand)
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals(smsCommand, smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains(replyLgs))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Current loop mode: $modeLgs", smsCommunicatorPlugin.messages[3].text)

        //NSCLIENT RESTART
        `when`(loop.isEnabled()).thenReturn(true)
        `when`(loop.isSuspended).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "NSCLIENT RESTART")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("NSCLIENT RESTART", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("NSCLIENT RESTART"))

        //NSCLIENT BLA BLA
        `when`(loop.isEnabled()).thenReturn(true)
        `when`(loop.isSuspended).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "NSCLIENT BLA BLA")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("NSCLIENT BLA BLA", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //NSCLIENT BLABLA
        `when`(loop.isEnabled()).thenReturn(true)
        `when`(loop.isSuspended).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "NSCLIENT BLABLA")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("NSCLIENT BLABLA", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PUMP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PUMP", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Virtual Pump", smsCommunicatorPlugin.messages[1].text)

        //PUMP CONNECT 1 2: wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP CONNECT 1 2")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("PUMP CONNECT 1 2", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PUMP CONNECT BLABLA
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP BLABLA")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("PUMP BLABLA", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PUMP CONNECT
        `when`(
            repository.runTransactionForResult(anyObject<Transaction<CancelCurrentOfflineEventIfAnyTransaction.TransactionResult>>())
        ).thenReturn(Single.just(CancelCurrentOfflineEventIfAnyTransaction.TransactionResult().apply {
        }))
        `when`(loop.enabled).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP CONNECT")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("PUMP CONNECT", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To connect pump reply with code "))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Pump reconnected", smsCommunicatorPlugin.messages[3].text)

        //PUMP DISCONNECT 1 2: wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 1 2")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("PUMP DISCONNECT 1 2", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PUMP DISCONNECT 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 0")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("Wrong duration", smsCommunicatorPlugin.messages[1].text)

        //PUMP DISCONNECT 30
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 30")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("PUMP DISCONNECT 30", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To disconnect pump for"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Pump disconnected", smsCommunicatorPlugin.messages[3].text)

        //PUMP DISCONNECT 30
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 200")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("PUMP DISCONNECT 200", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To disconnect pump for"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Pump disconnected", smsCommunicatorPlugin.messages[3].text)

        //HELP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "HELP")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("HELP", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("PUMP"))

        //HELP PUMP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "HELP PUMP")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("HELP PUMP", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("PUMP"))

        //SMS : wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "SMS")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("SMS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //SMS STOP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "SMS DISABLE")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("SMS DISABLE", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To disable the SMS Remote Service reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("SMS Remote Service stopped. To reactivate it, use AAPS on master smartphone."))

        //TARGET : wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertFalse(sms.ignored)
        Assertions.assertEquals("TARGET", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //TARGET MEAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET MEAL")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("TARGET MEAL", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To set the Temp Target"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("set successfully"))

        //TARGET STOP/CANCEL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET STOP")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("TARGET STOP", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To cancel Temp Target reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("Temp Target canceled successfully"))
    }

    @Test fun processProfileTest() {

        //PROFILE
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "PROFILE")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PROFILE", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)).thenReturn(true)

        //PROFILE
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PROFILE", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PROFILE LIST (no profile defined)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE LIST")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PROFILE LIST", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Not configured", smsCommunicatorPlugin.messages[1].text)

        `when`(profileSource.profile).thenReturn(getValidProfileStore())
        `when`(profileFunction.getProfileName()).thenReturn(TESTPROFILENAME)

        //PROFILE STATUS
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE STATUS")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PROFILE STATUS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals(TESTPROFILENAME, smsCommunicatorPlugin.messages[1].text)

        //PROFILE LIST
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE LIST")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PROFILE LIST", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("1. $TESTPROFILENAME", smsCommunicatorPlugin.messages[1].text)

        //PROFILE 2 (non existing)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 2")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PROFILE 2", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PROFILE 1 0(wrong percentage)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1 0")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PROFILE 1 0", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PROFILE 0(wrong index)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 0")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PROFILE 0", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PROFILE 1(OK)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PROFILE 1", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To switch profile to someProfile 100% reply with code"))

        //PROFILE 1 90(OK)
        `when`(profileFunction.createProfileSwitch(anyObject(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), anyLong())).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1 90")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("PROFILE 1 90", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To switch profile to someProfile 90% reply with code"))
        val passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Profile switch created", smsCommunicatorPlugin.messages[3].text)
    }

    @Test fun processBasalTest() {

        //BASAL
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "BASAL")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)).thenReturn(true)

        //BASAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //BASAL CANCEL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL CANCEL")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL CANCEL", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To stop temp basal reply with code"))
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("Temp basal canceled"))

        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        //BASAL a%
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL a%")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL a%", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //BASAL 10% 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 10% 0")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL 10% 0", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("TBR duration must be a multiple of 30 minutes and greater than 0.", smsCommunicatorPlugin.messages[1].text)

        //BASAL 20% 20
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 20% 20")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL 20% 20", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("TBR duration must be a multiple of 30 minutes and greater than 0.", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyBasalPercentConstraints(anyObject(), anyObject())).thenReturn(Constraint(20))

        //BASAL 20% 30
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 20% 30")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL 20% 30", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To start basal 20% for 30 min reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Temp basal 20% for 30 min started successfully\nVirtual Pump", smsCommunicatorPlugin.messages[3].text)

        //BASAL a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL a")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL a", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //BASAL 1 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 1 0")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL 1 0", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("TBR duration must be a multiple of 30 minutes and greater than 0.", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyBasalConstraints(anyObject(), anyObject())).thenReturn(Constraint(1.0))

        //BASAL 1 20
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 1 20")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL 1 20", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("TBR duration must be a multiple of 30 minutes and greater than 0.", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyBasalConstraints(anyObject(), anyObject())).thenReturn(Constraint(1.0))

        //BASAL 1 30
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 1 30")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BASAL 1 30", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To start basal 1.00U/h for 30 min reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Temp basal 1.00U/h for 30 min started successfully\nVirtual Pump", smsCommunicatorPlugin.messages[3].text)
    }

    @Test fun processExtendedTest() {

        //EXTENDED
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "EXTENDED")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("EXTENDED", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)).thenReturn(true)

        //EXTENDED
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("EXTENDED", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //EXTENDED CANCEL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED CANCEL")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("EXTENDED CANCEL", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To stop extended bolus reply with code"))
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("Extended bolus canceled"))

        //EXTENDED a%
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED a%")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("EXTENDED a%", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyExtendedBolusConstraints(anyObject())).thenReturn(Constraint(1.0))

        //EXTENDED 1 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED 1 0")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("EXTENDED 1 0", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //EXTENDED 1 20
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED 1 20")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("EXTENDED 1 20", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To start extended bolus 1.00U for 20 min reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Extended bolus 1.00U for 20 min started successfully\nVirtual Pump", smsCommunicatorPlugin.messages[3].text)
    }

    @Test fun processBolusTest() {

        //BOLUS
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "BOLUS")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BOLUS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)).thenReturn(true)

        //BOLUS
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BOLUS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(1.0))
        `when`(dateUtilMocked.now()).thenReturn(1000L)
        `when`(sp.getLong(R.string.key_smscommunicator_remote_bolus_min_distance, T.msecs(Constants.remoteBolusMinDistance).mins())).thenReturn(15L)
        //BOLUS 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BOLUS 1", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Remote bolus not available. Try again later.", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        `when`(dateUtilMocked.now()).thenReturn(Constants.remoteBolusMinDistance + 1002L)

        //BOLUS 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 0")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BOLUS 0", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //BOLUS a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS a")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BOLUS a", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyExtendedBolusConstraints(anyObject())).thenReturn(Constraint(1.0))
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(1.0))

        //BOLUS 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BOLUS 1", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To deliver bolus 1.00U reply with code"))
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("Bolus 1.00U delivered successfully"))

        //BOLUS 1 (Suspended pump)
        smsCommunicatorPlugin.lastRemoteBolusTime = 0
        testPumpPlugin.pumpSuspended = true
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BOLUS 1", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Pump suspended", smsCommunicatorPlugin.messages[1].text)
        testPumpPlugin.pumpSuspended = false

        //BOLUS 1 a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1 a")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BOLUS 1 a", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        //BOLUS 1 MEAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1 MEAL")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("BOLUS 1 MEAL", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To deliver meal bolus 1.00U reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Meal Bolus 1.00U delivered successfully\nVirtual Pump\nTarget 5.0 for 45 minutes", smsCommunicatorPlugin.messages[3].text)
    }

    @Test fun processCalTest() {

        //CAL
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "CAL")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CAL", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)).thenReturn(true)

        //CAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CAL")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CAL", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //CAL 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CAL 0")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CAL 0", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(xDripBroadcast.sendCalibration(ArgumentMatchers.anyDouble())).thenReturn(true)
        //CAL 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CAL 1")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CAL 1", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To send calibration 1.00 reply with code"))
        val passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertEquals("Calibration sent. Receiving must be enabled in xDrip.", smsCommunicatorPlugin.messages[3].text)
    }

    @Test fun processCarbsTest() {
        `when`(dateUtilMocked.now()).thenReturn(1000000L)
        `when`(dateUtilMocked.timeString(anyLong())).thenReturn("03:01AM")
        `when`(sp.getBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)).thenReturn(false)
        //CAL
        smsCommunicatorPlugin.messages = ArrayList()
        var sms = Sms("1234", "CARBS")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CARBS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)).thenReturn(true)

        //CARBS
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CARBS", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyCarbsConstraints(anyObject())).thenReturn(Constraint(0))

        //CARBS 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 0")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CARBS 0", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyCarbsConstraints(anyObject())).thenReturn(Constraint(1))

        //CARBS 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CARBS 1", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To enter 1g at"))
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[3].text.startsWith("Carbs 1g entered successfully"))

        //CARBS 1 a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 a")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CARBS 1 a", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("Wrong format"))

        //CARBS 1 00
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 00")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CARBS 1 00", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("Wrong format"))

        //CARBS 1 12:01
        `when`(dateUtilMocked.timeString(anyLong())).thenReturn("12:01PM")
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 12:01")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CARBS 1 12:01", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To enter 1g at 12:01PM reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[3].text.startsWith("Carbs 1g entered successfully"))

        //CARBS 1 3:01AM
        `when`(dateUtilMocked.timeString(anyLong())).thenReturn("03:01AM")
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 3:01AM")
        smsCommunicatorPlugin.processSms(sms)
        Assertions.assertEquals("CARBS 1 3:01AM", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To enter 1g at 03:01AM reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assertions.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assertions.assertTrue(smsCommunicatorPlugin.messages[3].text.startsWith("Carbs 1g entered successfully"))
    }

    @Test fun sendNotificationToAllNumbers() {
        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.sendNotificationToAllNumbers("abc")
        Assertions.assertEquals("abc", smsCommunicatorPlugin.messages[0].text)
        Assertions.assertEquals("abc", smsCommunicatorPlugin.messages[1].text)
    }
}