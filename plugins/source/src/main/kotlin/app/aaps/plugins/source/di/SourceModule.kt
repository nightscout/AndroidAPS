package app.aaps.plugins.source.di

import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.EversensePlugin
import app.aaps.plugins.source.NSClientSourcePlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.source.activities.EversenseCalibrationActivity
import app.aaps.plugins.source.activities.EversensePlacementActivity
import app.aaps.plugins.source.activities.EversenseStatusActivity
import app.aaps.plugins.source.activities.RequestDexcomPermissionActivity
import app.aaps.plugins.source.activities.RequestEversensePermissionActivity
import app.aaps.plugins.source.notificationreader.NotificationCollectorService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
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
    @ContributesAndroidInjector abstract fun contributesEversensePlugin(): EversensePlugin
    @ContributesAndroidInjector abstract fun contributesRequestEversensePermissionActivity(): RequestEversensePermissionActivity
    @ContributesAndroidInjector abstract fun contributesEversenseCalibrationActivity(): EversenseCalibrationActivity
    @ContributesAndroidInjector abstract fun contributesEversenseStatusActivity(): EversenseStatusActivity
    @ContributesAndroidInjector abstract fun contributesEversensePlacementActivity(): EversensePlacementActivity
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
    class Providers {
        @Provides
        @Singleton
        fun provideEversenseCGMPlugin(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): app.aaps.plugins.eversense.EversenseCGMPlugin {
            return app.aaps.plugins.eversense.EversenseCGMPlugin(context)
        }
    }
}