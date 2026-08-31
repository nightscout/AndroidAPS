package app.aaps.di.pump

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.pump.danars.comm.DanaRSPacket
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Provided from `:app` so `:pump:danars` needs no Dagger processor. */
@Module(includes = [DanaRSCommModule::class])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class DanaRSModule {

    companion object {

        @Provides
        fun providesCommands(
            @DanaRSCommModule.DanaRSCommand rsCommands: Set<@JvmSuppressWildcards DanaRSPacket>
        ): Set<@JvmSuppressWildcards DanaRSPacket> = rsCommands

        @Provides
        fun providesBluetoothAdapter(context: Context): BluetoothAdapter? =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    }
}
