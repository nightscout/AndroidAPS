package info.nightscout.androidaps.plugins.general.automation.actions

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.general.automation.elements.InputTempTarget
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest

@PrepareForTest(VirtualPumpPlugin::class, RxBusWrapper::class, LocalProfilePlugin::class, SmsCommunicatorPlugin::class, ConfigBuilderPlugin::class, LoopPlugin::class)
open class ActionsTestBase : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var loopPlugin: LoopPlugin
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var localProfilePlugin: LocalProfilePlugin
    @Mock lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActionStopTempTarget) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
                it.activePlugin = activePlugin
            }
            if (it is ActionStartTempTarget) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
                it.activePlugin = activePlugin
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
        `when`(profileFunction.getUnits()).thenReturn(Constants.MGDL)
    }
}