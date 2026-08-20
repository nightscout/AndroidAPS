package app.aaps.di

import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.core.interfaces.location.LocationServiceController
import app.aaps.receivers.TimerReminderReceiver
import app.aaps.services.LocationService
import app.aaps.services.LocationServiceControllerImpl
import app.aaps.services.ReminderSchedulerImpl
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The Android half of automation.
 *
 * :plugins:automation is being made multiplatform, and an Android `Service` or `BroadcastReceiver`
 * cannot live there: Dagger answers `@Inject lateinit` with a generated members injector written in
 * Java, and AGP's multiplatform library target has no Java compilation step - so the processor
 * produces nothing and the build still succeeds. The failure is silent, which is why these classes
 * were moved rather than left behind with the annotations stripped.
 *
 * Automation reaches both through interfaces in :core:interfaces commonMain, so an iOS build can
 * supply its own implementations (CoreLocation, local notifications) without touching plugin code.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class AutomationAndroidModule {

    @ContributesAndroidInjector abstract fun contributesLocationService(): LocationService
    @ContributesAndroidInjector abstract fun contributesTimerReminderReceiver(): TimerReminderReceiver

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindLocationServiceController(impl: LocationServiceControllerImpl): LocationServiceController

        @Binds fun bindReminderScheduler(impl: ReminderSchedulerImpl): ReminderScheduler
    }
}
