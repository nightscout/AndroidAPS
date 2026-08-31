package app.aaps.di.pump

import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManager
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dash driver interface bindings, provided from `:app` so `:pump:omnipod:dash` needs no Dagger
 * processor. The plugin registration is not here - that is already Metro's, on the plugin class.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class OmnipodDashModule {

    @Binds
    abstract fun bindsOmnipodDashBleManagerImpl(bleManager: OmnipodDashBleManagerImpl): OmnipodDashBleManager

    @Binds
    abstract fun bindsOmnipodDashPodStateManagerImpl(podStateManager: OmnipodDashPodStateManagerImpl): OmnipodDashPodStateManager

    @Binds
    abstract fun bindsOmnipodDashManagerImpl(omnipodManager: OmnipodDashManagerImpl): OmnipodDashManager
}
