package info.nightscout.androidaps.plugins.general.automation.actions

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.entities.OfflineEvent
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.general.automation.TestBaseWithProfile
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.`when`

open class
ActionsTestBase : TestBaseWithProfile() {

    open class TestLoopPlugin(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        injector: HasAndroidInjector,
        pluginDescription: PluginDescription
    ) : PluginBase(
        pluginDescription, aapsLogger, rh, injector
    ), Loop {

        private var suspended = false
        override var lastRun: Loop.LastRun? = Loop.LastRun()
        override val isSuspended: Boolean = suspended
        override val isLGS: Boolean = false
        override val isSuperBolus: Boolean = false
        override val isDisconnected: Boolean = false
        override var enabled: Boolean
            get() = true
            set(_) {}
        override var lastBgTriggeredRun: Long = 0

        override fun invoke(initiator: String, allowNotification: Boolean, tempBasalFallback: Boolean) {}
        override fun acceptChangeRequest() {}
        override fun minutesToEndOfSuspend(): Int = 0
        override fun goToZeroTemp(durationInMinutes: Int, profile: Profile, reason: OfflineEvent.Reason) {}
        override fun suspendLoop(durationInMinutes: Int) {}
        override fun disableCarbSuggestions(durationMinutes: Int) {}
    }

    @Mock lateinit var sp: SP
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var configBuilder: ConfigBuilder
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profilePlugin: ProfileSource
    @Mock lateinit var smsCommunicatorPlugin: SmsCommunicator
    @Mock lateinit var loopPlugin: TestLoopPlugin
    @Mock lateinit var uel: UserEntryLogger

    private val pluginDescription = PluginDescription()
    lateinit var testPumpPlugin: TestPumpPlugin

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActionStopTempTarget) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.dateUtil = dateUtil
                it.repository = repository
                it.uel = uel
            }
            if (it is ActionStartTempTarget) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
                it.repository = repository
                it.profileFunction = profileFunction
                it.uel = uel
                it.dateUtil = dateUtil
            }
            if (it is ActionSendSMS) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.smsCommunicatorPlugin = smsCommunicatorPlugin
            }
            if (it is ActionProfileSwitch) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
                it.profileFunction = profileFunction
                it.uel = uel
                it.dateUtil = dateUtil
            }
            if (it is ActionProfileSwitchPercent) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.profileFunction = profileFunction
                it.uel = uel
            }
            if (it is ActionNotification) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.rxBus = rxBus
            }
            if (it is ActionLoopSuspend) {
                it.aapsLogger = aapsLogger
                it.loop = loopPlugin
                it.rh = rh
                it.rxBus = rxBus
                it.uel = uel
            }
            if (it is ActionLoopResume) {
                it.aapsLogger = aapsLogger
                it.loopPlugin = loopPlugin
                it.rh = rh
                it.configBuilder = configBuilder
                it.rxBus = rxBus
                it.repository = repository
                it.dateUtil = dateUtil
                it.uel = uel
            }
            if (it is ActionLoopEnable) {
                it.aapsLogger = aapsLogger
                it.loopPlugin = loopPlugin
                it.rh = rh
                it.configBuilder = configBuilder
                it.rxBus = rxBus
                it.uel = uel
            }
            if (it is ActionLoopDisable) {
                it.aapsLogger = aapsLogger
                it.loopPlugin = loopPlugin
                it.rh = rh
                it.configBuilder = configBuilder
                it.commandQueue = commandQueue
                it.rxBus = rxBus
                it.uel = uel
            }
            if (it is ActionCarePortalEvent) {
                it.rh = rh
                it.repository = repository
                it.sp = sp
                it.dateUtil = dateUtil
                it.profileFunction = profileFunction
                it.uel = uel
            }
            if (it is ActionStopProcessing) {
                it.rh = rh
            }
            if (it is PumpEnactResult) {
                it.rh = rh
            }
            if (it is Trigger) {
                it.rh = rh
                it.profileFunction = profileFunction
            }
        }
    }

    @Before
    fun mock() {
        testPumpPlugin = TestPumpPlugin(pluginDescription, aapsLogger, rh, injector)
        `when`(activePlugin.activePump).thenReturn(testPumpPlugin)
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        `when`(activePlugin.activeProfileSource).thenReturn(profilePlugin)
        `when`(profilePlugin.profile).thenReturn(getValidProfileStore())

        `when`(rh.gs(R.string.ok)).thenReturn("OK")
        `when`(rh.gs(R.string.error)).thenReturn("Error")
    }
}