package info.nightscout.androidaps.plugins.general.automation.actions

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.elements.InputTempTarget
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest

@PrepareForTest(RxBusWrapper::class, ActionsTestBase.TestLoopPlugin::class, AppRepository::class)
open class ActionsTestBase : TestBaseWithProfile() {

    open class TestLoopPlugin(
        aapsLogger: AAPSLogger,
        resourceHelper: ResourceHelper,
        injector: HasAndroidInjector,
        pluginDescription: PluginDescription
    ) : PluginBase(
        pluginDescription, aapsLogger, resourceHelper, injector
    ), LoopInterface {

        var suspended = false
        override var lastRun: LoopInterface.LastRun? = LoopInterface.LastRun()
        override val isSuspended: Boolean = suspended
        override fun suspendTo(endTime: Long) {}
        override fun createOfflineEvent(durationInMinutes: Int) {}
        override fun suspendLoop(durationInMinutes: Int) {}
    }

    @Mock lateinit var sp: SP
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var configBuilderPlugin: ConfigBuilderInterface
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var profilePlugin: ProfileInterface
    @Mock lateinit var smsCommunicatorPlugin: SmsCommunicatorInterface
    @Mock lateinit var loopPlugin: TestLoopPlugin
    @Mock lateinit var repository: AppRepository

    private val pluginDescription = PluginDescription()
    lateinit var testPumpPlugin: TestPumpPlugin

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActionStopTempTarget) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
                it.activePlugin = activePlugin
                it.repository = repository
            }
            if (it is ActionStartTempTarget) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
                it.activePlugin = activePlugin
                it.repository = repository
            }
            if (it is ActionSendSMS) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
                it.smsCommunicatorPlugin = smsCommunicatorPlugin
            }
            if (it is ActionProfileSwitch) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
                it.activePlugin = activePlugin
                it.profileFunction = profileFunction
            }
            if (it is ActionProfileSwitchPercent) {
                it.resourceHelper = resourceHelper
                it.activePlugin = activePlugin
            }
            if (it is ActionNotification) {
                it.resourceHelper = resourceHelper
                it.rxBus = rxBus
            }
            if (it is ActionLoopSuspend) {
                it.loopPlugin = loopPlugin
                it.resourceHelper = resourceHelper
                it.rxBus = rxBus
            }
            if (it is ActionLoopResume) {
                it.loopPlugin = loopPlugin
                it.resourceHelper = resourceHelper
                it.configBuilderPlugin = configBuilderPlugin
                it.rxBus = rxBus
            }
            if (it is ActionLoopEnable) {
                it.loopPlugin = loopPlugin
                it.resourceHelper = resourceHelper
                it.configBuilderPlugin = configBuilderPlugin
                it.rxBus = rxBus
            }
            if (it is ActionLoopDisable) {
                it.loopPlugin = loopPlugin
                it.resourceHelper = resourceHelper
                it.configBuilderPlugin = configBuilderPlugin
                it.commandQueue = commandQueue
                it.rxBus = rxBus
            }
            if (it is PumpEnactResult) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
            }
            if (it is InputTempTarget) {
                it.profileFunction = profileFunction
            }
        }
    }

    @Before
    fun mock() {
        testPumpPlugin = TestPumpPlugin(pluginDescription, aapsLogger, resourceHelper, injector)
        `when`(activePlugin.activePump).thenReturn(testPumpPlugin)
        `when`(profileFunction.getUnits()).thenReturn(Constants.MGDL)
        `when`(activePlugin.activeProfileInterface).thenReturn(profilePlugin)
        `when`(profilePlugin.profile).thenReturn(getValidProfileStore())
    }
}