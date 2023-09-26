package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.database.entities.OfflineEvent
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.shared.tests.TestBaseWithProfile
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.database.impl.AppRepository
import org.junit.jupiter.api.BeforeEach
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
        override var closedLoopEnabled: Constraint<Boolean>? = ConstraintObject(false, aapsLogger)
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
        override fun buildAndStoreDeviceStatus() {}

        override fun setPluginEnabled(type: PluginType, newState: Boolean) {}
    }

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var configBuilder: ConfigBuilder
    @Mock lateinit var profilePlugin: ProfileSource
    @Mock lateinit var smsCommunicator: SmsCommunicator
    @Mock lateinit var loopPlugin: TestLoopPlugin
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var repository: AppRepository

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
                it.profileUtil = profileUtil
            }
            if (it is ActionSendSMS) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.smsCommunicator = smsCommunicator
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
                it.context = context
            }
            if (it is Trigger) {
                it.rh = rh
                it.profileFunction = profileFunction
                it.aapsLogger = aapsLogger
            }
            if (it is Action) {
                it.rh = rh
                it.aapsLogger = aapsLogger
            }
        }
    }

    @BeforeEach
    fun mock() {
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        `when`(activePlugin.activeProfileSource).thenReturn(profilePlugin)
        `when`(profilePlugin.profile).thenReturn(getValidProfileStore())

        `when`(context.getString(app.aaps.core.ui.R.string.ok)).thenReturn("OK")
        `when`(context.getString(app.aaps.core.ui.R.string.error)).thenReturn("Error")
    }
}