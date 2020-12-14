package info.nightscout.androidaps.plugins.general.smsCommunicator

import android.content.Context
import android.telephony.SmsManager
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentService
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.XdripCalibrations
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, FabricPrivacy::class, VirtualPumpPlugin::class, XdripCalibrations::class, SmsManager::class, CommandQueue::class, LocalProfilePlugin::class, DateUtil::class, IobCobCalculatorPlugin::class, OneTimePassword::class)
class SmsCommunicatorPluginTest : TestBaseWithProfile() {

    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var loopPlugin: LoopPlugin
    @Mock lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var localProfilePlugin: LocalProfilePlugin
    @Mock lateinit var treatmentService: TreatmentService
    @Mock lateinit var otp: OneTimePassword
    @Mock lateinit var xdripCalibrations: XdripCalibrations

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is PumpEnactResult) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
            }
            if (it is BgReading) {
                it.aapsLogger = aapsLogger
                it.defaultValueHelper = defaultValueHelper
                it.resourceHelper = resourceHelper
                it.profileFunction = profileFunction
            }
            if (it is AuthRequest) {
                it.aapsLogger = aapsLogger
                it.smsCommunicatorPlugin = smsCommunicatorPlugin
                it.resourceHelper = resourceHelper
                it.otp = otp
            }
            if (it is GlucoseStatus) {
                it.aapsLogger = aapsLogger
                it.iobCobCalculatorPlugin = iobCobCalculatorPlugin
            }
        }
    }

    private lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    private var hasBeenRun = false

    @Before fun prepareTests() {
        val reading = BgReading(injector)
        reading.value = 100.0
        val bgList: MutableList<BgReading> = ArrayList()
        bgList.add(reading)

        `when`(iobCobCalculatorPlugin.dataLock).thenReturn(Unit)
        `when`(iobCobCalculatorPlugin.bgReadings).thenReturn(bgList)
        `when`(iobCobCalculatorPlugin.getCobInfo(false, "SMS COB")).thenReturn(CobInfo(10.0, 2.0))
        `when`(iobCobCalculatorPlugin.lastBg()).thenReturn(reading)

        PowerMockito.spy(DateUtil::class.java)
        PowerMockito.mockStatic(SmsManager::class.java)
        val smsManager = PowerMockito.mock(SmsManager::class.java)
        `when`(SmsManager.getDefault()).thenReturn(smsManager)
        `when`(sp.getString(R.string.key_smscommunicator_allowednumbers, "")).thenReturn("1234;5678")

        smsCommunicatorPlugin = SmsCommunicatorPlugin(injector, aapsLogger, resourceHelper, sp, constraintChecker, rxBus, profileFunction, fabricPrivacy, activePlugin, commandQueue, loopPlugin, iobCobCalculatorPlugin, xdripCalibrations, otp, Config(), DateUtil(context, resourceHelper))
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
            val callback = invocation.getArgument<Callback>(4)
            callback.result = PumpEnactResult(injector).success(true).isPercent(true).percent(invocation.getArgument(0)).duration(invocation.getArgument(1))
            callback.run()
            null
        }.`when`(commandQueue).tempBasalPercent(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyBoolean(), anyObject(), ArgumentMatchers.any(Callback::class.java))
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(4)
            callback.result = PumpEnactResult(injector).success(true).isPercent(false).absolute(invocation.getArgument(0)).duration(invocation.getArgument(1))
            callback.run()
            null
        }.`when`(commandQueue).tempBasalAbsolute(ArgumentMatchers.anyDouble(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyBoolean(), anyObject(), ArgumentMatchers.any(Callback::class.java))
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val callback = invocation.getArgument<Callback>(2)
            callback.result = PumpEnactResult(injector).success(true).isPercent(false).absolute(invocation.getArgument(0)).duration(invocation.getArgument(1))
            callback.run()
            null
        }.`when`(commandQueue).extendedBolus(ArgumentMatchers.anyDouble(), ArgumentMatchers.anyInt(), ArgumentMatchers.any(Callback::class.java))

        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(activePlugin.activeTreatments).thenReturn(treatmentsPlugin)

        `when`(virtualPumpPlugin.shortStatus(ArgumentMatchers.anyBoolean())).thenReturn("Virtual Pump")
        `when`(virtualPumpPlugin.isSuspended).thenReturn(false)
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(PumpDescription())
        `when`(virtualPumpPlugin.model()).thenReturn(PumpType.GenericAAPS);

        `when`(treatmentsPlugin.lastCalculationTreatments).thenReturn(IobTotal(0))
        `when`(treatmentsPlugin.lastCalculationTempBasals).thenReturn(IobTotal(0))
        `when`(treatmentsPlugin.service).thenReturn(treatmentService)

        `when`(activePlugin.activeProfileInterface).thenReturn(localProfilePlugin)

        `when`(profileFunction.getUnits()).thenReturn(Constants.MGDL)

        `when`(resourceHelper.gs(R.string.smscommunicator_remotecommandnotallowed)).thenReturn("Remote command is not allowed")
        `when`(resourceHelper.gs(R.string.sms_wrongcode)).thenReturn("Wrong code. Command cancelled.")
        `when`(resourceHelper.gs(R.string.sms_iob)).thenReturn("IOB:")
        `when`(resourceHelper.gs(R.string.sms_lastbg)).thenReturn("Last BG:")
        `when`(resourceHelper.gs(R.string.sms_minago)).thenReturn("%1\$dmin ago")
        `when`(resourceHelper.gs(R.string.smscommunicator_remotecommandnotallowed)).thenReturn("Remote command is not allowed")
        `when`(resourceHelper.gs(R.string.smscommunicator_stopsmswithcode)).thenReturn("To disable the SMS Remote Service reply with code %1\$s.\\n\\nKeep in mind that you\\'ll able to reactivate it directly from the AAPS master smartphone only.")
        `when`(resourceHelper.gs(R.string.smscommunicator_mealbolusreplywithcode)).thenReturn("To deliver meal bolus %1$.2fU reply with code %2\$s.")
        `when`(resourceHelper.gs(R.string.smscommunicator_temptargetwithcode)).thenReturn("To set the Temp Target %1\$s reply with code %2\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_temptargetcancel)).thenReturn("To cancel Temp Target reply with code %1\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_stoppedsms)).thenReturn("SMS Remote Service stopped. To reactivate it, use AAPS on master smartphone.")
        `when`(resourceHelper.gs(R.string.smscommunicator_tt_set)).thenReturn("Target %1\$s for %2\$d minutes set successfully")
        `when`(resourceHelper.gs(R.string.smscommunicator_tt_canceled)).thenReturn("Temp Target canceled successfully")
        `when`(resourceHelper.gs(R.string.loopsuspendedfor)).thenReturn("Suspended (%1\$d m)")
        `when`(resourceHelper.gs(R.string.smscommunicator_loopisdisabled)).thenReturn("Loop is disabled")
        `when`(resourceHelper.gs(R.string.smscommunicator_loopisenabled)).thenReturn("Loop is enabled")
        `when`(resourceHelper.gs(R.string.wrongformat)).thenReturn("Wrong format")
        `when`(resourceHelper.gs(eq(R.string.wrongTbrDuration), any())).thenAnswer({ i: InvocationOnMock -> "TBR duration must be a multiple of " + i.getArguments()[1] + " minutes and greater than 0."})
        `when`(resourceHelper.gs(R.string.smscommunicator_loophasbeendisabled)).thenReturn("Loop has been disabled")
        `when`(resourceHelper.gs(R.string.smscommunicator_loophasbeenenabled)).thenReturn("Loop has been enabled")
        `when`(resourceHelper.gs(R.string.smscommunicator_tempbasalcanceled)).thenReturn("Temp basal canceled")
        `when`(resourceHelper.gs(R.string.smscommunicator_loopresumed)).thenReturn("Loop resumed")
        `when`(resourceHelper.gs(R.string.smscommunicator_wrongduration)).thenReturn("Wrong duration")
        `when`(resourceHelper.gs(R.string.smscommunicator_suspendreplywithcode)).thenReturn("To suspend loop for %1\$d minutes reply with code %2\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_loopsuspended)).thenReturn("Loop suspended")
        `when`(resourceHelper.gs(R.string.smscommunicator_unknowncommand)).thenReturn("Unknown command or wrong reply")
        `when`(resourceHelper.gs(R.string.notconfigured)).thenReturn("Not configured")
        `when`(resourceHelper.gs(R.string.smscommunicator_profilereplywithcode)).thenReturn("To switch profile to %1\$s %2\$d%% reply with code %3\$s")
        `when`(resourceHelper.gs(R.string.profileswitchcreated)).thenReturn("Profile switch created")
        `when`(resourceHelper.gs(R.string.smscommunicator_basalstopreplywithcode)).thenReturn("To stop temp basal reply with code %1\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_basalpctreplywithcode)).thenReturn("To start basal %1\$d%% for %2\$d min reply with code %3\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_tempbasalset_percent)).thenReturn("Temp basal %1\$d%% for %2\$d min started successfully")
        `when`(resourceHelper.gs(R.string.smscommunicator_basalreplywithcode)).thenReturn("To start basal %1$.2fU/h for %2\$d min reply with code %3\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_tempbasalset)).thenReturn("Temp basal %1$.2fU/h for %2\$d min started successfully")
        `when`(resourceHelper.gs(R.string.smscommunicator_extendedstopreplywithcode)).thenReturn("To stop extended bolus reply with code %1\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_extendedcanceled)).thenReturn("Extended bolus canceled")
        `when`(resourceHelper.gs(R.string.smscommunicator_extendedreplywithcode)).thenReturn("To start extended bolus %1$.2fU for %2\$d min reply with code %3\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_extendedset)).thenReturn("Extended bolus %1$.2fU for %2\$d min started successfully")
        `when`(resourceHelper.gs(R.string.smscommunicator_bolusreplywithcode)).thenReturn("To deliver bolus %1$.2fU reply with code %2\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_bolusdelivered)).thenReturn("Bolus %1$.2fU delivered successfully")
        `when`(resourceHelper.gs(R.string.smscommunicator_remotebolusnotallowed)).thenReturn("Remote bolus not available. Try again later.")
        `when`(resourceHelper.gs(R.string.smscommunicator_calibrationreplywithcode)).thenReturn("To send calibration %1$.2f reply with code %2\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_calibrationsent)).thenReturn("Calibration sent. Receiving must be enabled in xDrip.")
        `when`(resourceHelper.gs(R.string.smscommunicator_carbsreplywithcode)).thenReturn("To enter %1\$dg at %2\$s reply with code %3\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_carbsset)).thenReturn("Carbs %1\$dg entered successfully")
        `when`(resourceHelper.gs(R.string.noprofile)).thenReturn("No profile loaded from NS yet")
        `when`(resourceHelper.gs(R.string.pumpsuspended)).thenReturn("Pump suspended")
        `when`(resourceHelper.gs(R.string.sms_delta)).thenReturn("Delta:")
        `when`(resourceHelper.gs(R.string.sms_bolus)).thenReturn("Bolus:")
        `when`(resourceHelper.gs(R.string.sms_basal)).thenReturn("Basal:")
        `when`(resourceHelper.gs(R.string.cob)).thenReturn("COB")
        `when`(resourceHelper.gs(R.string.smscommunicator_mealbolusdelivered)).thenReturn("Meal Bolus %1\$.2fU delivered successfully")
        `when`(resourceHelper.gs(R.string.smscommunicator_mealbolusdelivered_tt)).thenReturn("Target %1\$s for %2\$d minutes")
        `when`(resourceHelper.gs(R.string.sms_actualbg)).thenReturn("BG:")
        `when`(resourceHelper.gs(R.string.sms_lastbg)).thenReturn("Last BG:")
        `when`(resourceHelper.gs(R.string.smscommunicator_loopdisablereplywithcode)).thenReturn("To disable loop reply with code %1\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_loopenablereplywithcode)).thenReturn("To enable loop reply with code %1\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_loopresumereplywithcode)).thenReturn("To resume loop reply with code %1\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_pumpdisconnectwithcode)).thenReturn("To disconnect pump for %1d minutes reply with code %2\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_pumpconnectwithcode)).thenReturn("To connect pump reply with code %1\$s")
        `when`(resourceHelper.gs(R.string.smscommunicator_reconnect)).thenReturn("Pump reconnected")
        `when`(resourceHelper.gs(R.string.smscommunicator_pumpconnectfail)).thenReturn("Connection to pump failed")
        `when`(resourceHelper.gs(R.string.smscommunicator_pumpdisconnected)).thenReturn("Pump disconnected")

    }

    @Test
    fun processSettingsTest() {
        // called from constructor
        Assert.assertEquals("1234", smsCommunicatorPlugin.allowedNumbers.get(0))
        Assert.assertEquals("5678", smsCommunicatorPlugin.allowedNumbers.get(1))
        Assert.assertEquals(2, smsCommunicatorPlugin.allowedNumbers.size)
    }

    @Test
    fun isCommandTest() {
        Assert.assertTrue(smsCommunicatorPlugin.isCommand("BOLUS", ""))
        smsCommunicatorPlugin.messageToConfirm = null
        Assert.assertFalse(smsCommunicatorPlugin.isCommand("BLB", ""))
        smsCommunicatorPlugin.messageToConfirm = AuthRequest(injector, Sms("1234", "ddd"), "RequestText", "ccode", object : SmsAction() {
            override fun run() {}
        })
        Assert.assertTrue(smsCommunicatorPlugin.isCommand("BLB", "1234"))
        Assert.assertFalse(smsCommunicatorPlugin.isCommand("BLB", "2345"))
        smsCommunicatorPlugin.messageToConfirm = null
    }

    @Test fun _isAllowedNumberTest() {
        Assert.assertTrue(smsCommunicatorPlugin.isAllowedNumber("5678"))
        Assert.assertFalse(smsCommunicatorPlugin.isAllowedNumber("56"))
    }

    @Test fun processSmsTest() {
        var sms: Sms

        // SMS from not allowed number should be ignored
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("12", "aText")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertTrue(sms.ignored)
        Assert.assertEquals("aText", smsCommunicatorPlugin.messages[0].text)

        //UNKNOWN
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "UNKNOWN")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("UNKNOWN", smsCommunicatorPlugin.messages[0].text)

        //BG
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BG")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BG", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("IOB:"))
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("Last BG: 100"))
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("COB: 10(2)g"))

        // LOOP : test remote control disabled
        `when`(sp.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("Remote command is not allowed"))
        `when`(sp.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true)

        //LOOP STATUS : disabled
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Loop is disabled", smsCommunicatorPlugin.messages[1].text)

        //LOOP STATUS : suspended
        PowerMockito.`when`(loopPlugin.minutesToEndOfSuspend()).thenReturn(10)
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        PowerMockito.`when`(loopPlugin.isSuspended).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Suspended (10 m)", smsCommunicatorPlugin.messages[1].text)

        //LOOP STATUS : enabled
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        PowerMockito.`when`(loopPlugin.isSuspended).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP STATUS")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Loop is enabled", smsCommunicatorPlugin.messages[1].text)

        //LOOP : wrong format
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //LOOP DISABLE : already disabled
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP DISABLE")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP DISABLE", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Loop is disabled", smsCommunicatorPlugin.messages[1].text)

        //LOOP DISABLE : from enabled
        hasBeenRun = false
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        PowerMockito.doAnswer(Answer { _: InvocationOnMock? ->
            hasBeenRun = true
            null
        } as Answer<*>).`when`(loopPlugin).setPluginEnabled(PluginType.LOOP, false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP DISABLE")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP DISABLE", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To disable loop reply with code "))
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Loop has been disabled Temp basal canceled", smsCommunicatorPlugin.messages[3].text)
        Assert.assertTrue(hasBeenRun)

        //LOOP ENABLE : already enabled
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP ENABLE")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP ENABLE", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Loop is enabled", smsCommunicatorPlugin.messages[1].text)

        //LOOP ENABLE : from disabled
        hasBeenRun = false
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false)
        PowerMockito.doAnswer(Answer { _: InvocationOnMock? ->
            hasBeenRun = true
            null
        } as Answer<*>).`when`(loopPlugin).setPluginEnabled(PluginType.LOOP, true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP ENABLE")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP ENABLE", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To enable loop reply with code "))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Loop has been enabled", smsCommunicatorPlugin.messages[3].text)
        Assert.assertTrue(hasBeenRun)

        //LOOP RESUME : already enabled
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP RESUME")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP RESUME", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To resume loop reply with code "))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Loop resumed", smsCommunicatorPlugin.messages[3].text)

        //LOOP SUSPEND 1 2: wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 1 2")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP SUSPEND 1 2", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //LOOP SUSPEND 0 : wrong duration
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 0")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP SUSPEND 0", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong duration", smsCommunicatorPlugin.messages[1].text)

        //LOOP SUSPEND 100 : suspend for 100 min + correct answer
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 100")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP SUSPEND 100", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To suspend loop for 100 minutes reply with code "))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Loop suspended Temp basal canceled", smsCommunicatorPlugin.messages[3].text)

        //LOOP SUSPEND 200 : limit to 180 min + wrong answer
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP SUSPEND 200")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP SUSPEND 200", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To suspend loop for 180 minutes reply with code "))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        // ignore from other number
        smsCommunicatorPlugin.processSms(Sms("5678", passCode))
        smsCommunicatorPlugin.processSms(Sms("1234", "XXXX"))
        Assert.assertEquals("XXXX", smsCommunicatorPlugin.messages[3].text)
        Assert.assertEquals("Wrong code. Command cancelled.", smsCommunicatorPlugin.messages[4].text)
        //then correct code should not work
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[5].text)
        Assert.assertEquals(6, smsCommunicatorPlugin.messages.size.toLong()) // processed as common message

        //LOOP BLABLA
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "LOOP BLABLA")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("LOOP BLABLA", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //TREATMENTS REFRESH
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        PowerMockito.`when`(loopPlugin.isSuspended).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TREATMENTS REFRESH")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("TREATMENTS REFRESH", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("TREATMENTS REFRESH"))

        //TREATMENTS BLA BLA
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        PowerMockito.`when`(loopPlugin.isSuspended).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TREATMENTS BLA BLA")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("TREATMENTS BLA BLA", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //TREATMENTS BLABLA
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        PowerMockito.`when`(loopPlugin.isSuspended).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TREATMENTS BLABLA")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("TREATMENTS BLABLA", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //NSCLIENT RESTART
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        PowerMockito.`when`(loopPlugin.isSuspended).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "NSCLIENT RESTART")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("NSCLIENT RESTART", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("NSCLIENT RESTART"))

        //NSCLIENT BLA BLA
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        PowerMockito.`when`(loopPlugin.isSuspended).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "NSCLIENT BLA BLA")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("NSCLIENT BLA BLA", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //NSCLIENT BLABLA
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        PowerMockito.`when`(loopPlugin.isSuspended).thenReturn(false)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "NSCLIENT BLABLA")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("NSCLIENT BLABLA", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PUMP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PUMP", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Virtual Pump", smsCommunicatorPlugin.messages[1].text)

        //PUMP CONNECT 1 2: wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP CONNECT 1 2")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("PUMP CONNECT 1 2", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PUMP CONNECT BLABLA
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP BLABLA")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("PUMP BLABLA", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PUMP CONNECT
        PowerMockito.`when`(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP CONNECT")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("PUMP CONNECT", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To connect pump reply with code "))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Pump reconnected", smsCommunicatorPlugin.messages[3].text)

        //PUMP DISCONNECT 1 2: wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 1 2")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("PUMP DISCONNECT 1 2", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PUMP DISCONNECT 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 0")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("Wrong duration", smsCommunicatorPlugin.messages[1].text)

        //PUMP DISCONNECT 30
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 30")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("PUMP DISCONNECT 30", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To disconnect pump for"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Pump disconnected", smsCommunicatorPlugin.messages[3].text)

        //PUMP DISCONNECT 30
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PUMP DISCONNECT 200")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("PUMP DISCONNECT 200", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To disconnect pump for"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Pump disconnected", smsCommunicatorPlugin.messages[3].text)

        //HELP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "HELP")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("HELP", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("PUMP"))

        //HELP PUMP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "HELP PUMP")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("HELP PUMP", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("PUMP"))

        //SMS : wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "SMS")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("SMS", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //SMS STOP
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "SMS DISABLE")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("SMS DISABLE", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To disable the SMS Remote Service reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("SMS Remote Service stopped. To reactivate it, use AAPS on master smartphone."))

        //TARGET : wrong format
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertFalse(sms.ignored)
        Assert.assertEquals("TARGET", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //TARGET MEAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET MEAL")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("TARGET MEAL", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To set the Temp Target"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("set successfully"))

        //TARGET STOP/CANCEL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "TARGET STOP")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("TARGET STOP", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To cancel Temp Target reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("Temp Target canceled successfully"))
    }

    @Test fun processProfileTest() {
        var sms: Sms

        //PROFILE
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PROFILE", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true)

        //PROFILE
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PROFILE", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PROFILE LIST (no profile defined)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE LIST")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PROFILE LIST", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Not configured", smsCommunicatorPlugin.messages[1].text)

        `when`(localProfilePlugin.profile).thenReturn(getValidProfileStore())
        `when`(profileFunction.getProfileName()).thenReturn(TESTPROFILENAME)

        //PROFILE STATUS
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE STATUS")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PROFILE STATUS", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals(TESTPROFILENAME, smsCommunicatorPlugin.messages[1].text)

        //PROFILE LIST
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE LIST")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PROFILE LIST", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("1. " + TESTPROFILENAME, smsCommunicatorPlugin.messages[1].text)

        //PROFILE 2 (non existing)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 2")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PROFILE 2", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PROFILE 1 0(wrong percentage)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1 0")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PROFILE 1 0", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PROFILE 0(wrong index)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 0")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PROFILE 0", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //PROFILE 1(OK)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PROFILE 1", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To switch profile to someProfile 100% reply with code"))

        //PROFILE 1 90(OK)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "PROFILE 1 90")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("PROFILE 1 90", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To switch profile to someProfile 90% reply with code"))
        val passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Profile switch created", smsCommunicatorPlugin.messages[3].text)
    }

    @Test fun processBasalTest() {
        var sms: Sms

        //BASAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true)

        //BASAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //BASAL CANCEL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL CANCEL")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL CANCEL", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To stop temp basal reply with code"))
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("Temp basal canceled"))

        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        //BASAL a%
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL a%")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL a%", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //BASAL 10% 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 10% 0")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL 10% 0", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("TBR duration must be a multiple of 30 minutes and greater than 0.", smsCommunicatorPlugin.messages[1].text)

        //BASAL 20% 20
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 20% 20")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL 20% 20", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("TBR duration must be a multiple of 30 minutes and greater than 0.", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyBasalPercentConstraints(anyObject(), anyObject())).thenReturn(Constraint<Int>(20))

        //BASAL 20% 30
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 20% 30")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL 20% 30", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To start basal 20% for 30 min reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Temp basal 20% for 30 min started successfully\nVirtual Pump", smsCommunicatorPlugin.messages[3].text)

        //BASAL a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL a")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL a", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //BASAL 1 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 1 0")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL 1 0", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("TBR duration must be a multiple of 30 minutes and greater than 0.", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyBasalConstraints(anyObject(), anyObject())).thenReturn(Constraint<Double>(1.0))

        //BASAL 1 20
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 1 20")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL 1 20", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("TBR duration must be a multiple of 30 minutes and greater than 0.", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyBasalConstraints(anyObject(), anyObject())).thenReturn(Constraint<Double>(1.0))

        //BASAL 1 30
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BASAL 1 30")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BASAL 1 30", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To start basal 1.00U/h for 30 min reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Temp basal 1.00U/h for 30 min started successfully\nVirtual Pump", smsCommunicatorPlugin.messages[3].text)
    }

    @Test fun processExtendedTest() {
        var sms: Sms

        //EXTENDED
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("EXTENDED", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true)

        //EXTENDED
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("EXTENDED", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //EXTENDED CANCEL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED CANCEL")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("EXTENDED CANCEL", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To stop extended bolus reply with code"))
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("Extended bolus canceled"))

        //EXTENDED a%
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED a%")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("EXTENDED a%", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyExtendedBolusConstraints(anyObject())).thenReturn(Constraint<Double>(1.0))

        //EXTENDED 1 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED 1 0")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("EXTENDED 1 0", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //EXTENDED 1 20
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "EXTENDED 1 20")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("EXTENDED 1 20", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To start extended bolus 1.00U for 20 min reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Extended bolus 1.00U for 20 min started successfully\nnull\nVirtual Pump", smsCommunicatorPlugin.messages[3].text)
    }

    @Test fun processBolusTest() {
        var sms: Sms

        //BOLUS
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BOLUS", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true)

        //BOLUS
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BOLUS", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint<Double>(1.0))
        PowerMockito.`when`(DateUtil.now()).thenReturn(1000L)
        `when`(sp.getLong(R.string.key_smscommunicator_remotebolusmindistance, T.msecs(Constants.remoteBolusMinDistance).mins())).thenReturn(15L)
        //BOLUS 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BOLUS 1", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Remote bolus not available. Try again later.", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint<Double>(0.0))
        PowerMockito.`when`(DateUtil.now()).thenReturn(Constants.remoteBolusMinDistance + 1002L)

        //BOLUS 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 0")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BOLUS 0", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //BOLUS a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS a")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BOLUS a", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyExtendedBolusConstraints(anyObject())).thenReturn(Constraint<Double>(1.0))
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint<Double>(1.0))

        //BOLUS 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BOLUS 1", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To deliver bolus 1.00U reply with code"))
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[3].text.contains("Bolus 1.00U delivered successfully"))

        //BOLUS 1 (Suspended pump)
        smsCommunicatorPlugin.lastRemoteBolusTime = 0
        PowerMockito.`when`(virtualPumpPlugin.isSuspended).thenReturn(true)
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BOLUS 1", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Pump suspended", smsCommunicatorPlugin.messages[1].text)
        PowerMockito.`when`(virtualPumpPlugin.isSuspended).thenReturn(false)

        //BOLUS 1 a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1 a")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BOLUS 1 a", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        //BOLUS 1 MEAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "BOLUS 1 MEAL")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("BOLUS 1 MEAL", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To deliver meal bolus 1.00U reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Meal Bolus 1.00U delivered successfully\nVirtual Pump\nTarget 5.0 for 45 minutes", smsCommunicatorPlugin.messages[3].text)
    }

    @Test fun processCalTest() {
        var sms: Sms

        //CAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CAL")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CAL", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true)

        //CAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CAL")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CAL", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)

        //CAL 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CAL 0")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CAL 0", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        PowerMockito.`when`(xdripCalibrations.sendIntent(ArgumentMatchers.anyDouble())).thenReturn(true)
        //CAL 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CAL 1")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CAL 1", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To send calibration 1.00 reply with code"))
        val passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertEquals("Calibration sent. Receiving must be enabled in xDrip.", smsCommunicatorPlugin.messages[3].text)
    }

    @Test fun processCarbsTest() {
        var sms: Sms
        PowerMockito.`when`(DateUtil.now()).thenReturn(1000000L)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(false)
        //CAL
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CARBS", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages[1].text)
        `when`(sp.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true)

        //CARBS
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CARBS", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyCarbsConstraints(anyObject())).thenReturn(Constraint<Int>(0))

        //CARBS 0
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 0")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CARBS 0", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages[1].text)
        `when`(constraintChecker.applyCarbsConstraints(anyObject())).thenReturn(Constraint<Int>(1))

        //CARBS 1
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CARBS 1", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To enter 1g at"))
        var passCode: String = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[3].text.startsWith("Carbs 1g entered successfully"))

        //CARBS 1 a
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 a")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CARBS 1 a", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("Wrong format"))

        //CARBS 1 00
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 00")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CARBS 1 00", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("Wrong format"))

        //CARBS 1 12:01
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 12:01")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CARBS 1 12:01", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To enter 1g at 12:01PM reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[3].text.startsWith("Carbs 1g entered successfully"))

        //CARBS 1 3:01AM
        smsCommunicatorPlugin.messages = ArrayList()
        sms = Sms("1234", "CARBS 1 3:01AM")
        smsCommunicatorPlugin.processSms(sms)
        Assert.assertEquals("CARBS 1 3:01AM", smsCommunicatorPlugin.messages[0].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[1].text.contains("To enter 1g at 03:01AM reply with code"))
        passCode = smsCommunicatorPlugin.messageToConfirm?.confirmCode!!
        smsCommunicatorPlugin.processSms(Sms("1234", passCode))
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages[2].text)
        Assert.assertTrue(smsCommunicatorPlugin.messages[3].text.startsWith("Carbs 1g entered successfully"))
    }

    @Test fun sendNotificationToAllNumbers() {
        smsCommunicatorPlugin.messages = ArrayList()
        smsCommunicatorPlugin.sendNotificationToAllNumbers("abc")
        Assert.assertEquals("abc", smsCommunicatorPlugin.messages[0].text)
        Assert.assertEquals("abc", smsCommunicatorPlugin.messages[1].text)
    }
}