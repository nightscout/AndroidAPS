package app.aaps.pump.danars.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.comm.DanaRSPacket
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module(
    includes = [
        DanaRSCommModule::class,
        DanaRSServicesModule::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class DanaRSModule {

    companion object {

        @Provides
        fun providesCommands(
            @DanaRSCommModule.DanaRSCommand rsCommands: Set<@JvmSuppressWildcards DanaRSPacket>,
        ): Set<@JvmSuppressWildcards DanaRSPacket> = rsCommands

        @Provides
        fun providesBluetoothAdapter(context: Context): BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    }

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1040)
    abstract fun bindDanaRSPlugin(plugin: DanaRSPlugin): PluginBase
}
