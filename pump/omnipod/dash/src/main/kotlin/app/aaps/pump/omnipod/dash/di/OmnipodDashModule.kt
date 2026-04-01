package app.aaps.pump.omnipod.dash.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManager
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl
import app.aaps.pump.omnipod.common.di.OmnipodCommonBleModule
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module(includes = [OmnipodDashHistoryModule::class, OmnipodCommonBleModule::class])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class OmnipodDashModule {

    // MANAGERS

    @Binds
    abstract fun bindsOmnipodDashBleManagerImpl(bleManager: OmnipodDashBleManagerImpl): OmnipodDashBleManager

    @Binds
    abstract fun bindsOmnipodDashPodStateManagerImpl(podStateManager: OmnipodDashPodStateManagerImpl): OmnipodDashPodStateManager

    @Binds
    abstract fun bindsOmnipodDashManagerImpl(omnipodManager: OmnipodDashManagerImpl): OmnipodDashManager

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1080)
    abstract fun bindOmnipodDashPumpPlugin(plugin: OmnipodDashPumpPlugin): PluginBase
}
