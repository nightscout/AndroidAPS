package app.aaps.plugins.source.di

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.NSClientSourcePlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.source.activities.RequestDexcomPermissionActivity
import app.aaps.plugins.source.notificationreader.NotificationCollectorService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module(
    includes = [
        SourceModule.Bindings::class,
        SourceModule.Providers::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class SourceModule {

    // All BG-source workers migrated to @HiltWorker (constructed by HiltWorkerFactory).
    @ContributesAndroidInjector abstract fun contributesRequestDexcomPermissionActivity(): RequestDexcomPermissionActivity
    @ContributesAndroidInjector abstract fun contributesEversensePlugin(): app.aaps.plugins.source.EversensePlugin
    @ContributesAndroidInjector abstract fun contributesRequestEversensePermissionActivity(): app.aaps.plugins.source.activities.RequestEversensePermissionActivity
    @ContributesAndroidInjector abstract fun contributesEversenseCalibrationActivity(): app.aaps.plugins.source.activities.EversenseCalibrationActivity
    @ContributesAndroidInjector abstract fun contributesEversenseStatusActivity(): app.aaps.plugins.source.activities.EversenseStatusActivity
    @ContributesAndroidInjector abstract fun contributesEversensePlacementActivity(): app.aaps.plugins.source.activities.EversensePlacementActivity
    @ContributesAndroidInjector abstract fun contributesNotificationCollectorService(): NotificationCollectorService

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindNSClientSource(nsClientSourcePlugin: NSClientSourcePlugin): NSClientSource
        @Binds fun bindDexcomBoyda(dexcomPlugin: DexcomPlugin): DexcomBoyda
        @Binds fun bindXDrip(xdripSourcePlugin: XdripSourcePlugin): XDripSource
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object Providers {

        @Provides
        @Singleton
        fun provideEversenseCGMPlugin(
            @ApplicationContext context: Context
        ): EversenseCGMPlugin {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val preferences = context.getSharedPreferences("EversenseCGMManager", Context.MODE_PRIVATE)
            return EversenseCGMPlugin(context, bluetoothManager, preferences)
        }
    }
}