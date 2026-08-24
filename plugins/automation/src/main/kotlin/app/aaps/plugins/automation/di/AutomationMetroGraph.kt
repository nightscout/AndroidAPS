package app.aaps.plugins.automation.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.TimerReminderReceiver
import app.aaps.plugins.automation.services.LastLocationDataContainer
import app.aaps.plugins.automation.services.LocationService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides
import kotlin.reflect.KClass

/**
 * Metro wiring for automation's two Android entry points.
 *
 * Both used to live in `:app`, because Dagger answers `@Inject lateinit` with a members injector
 * written in **Java**, and AGP's multiplatform library target has no Java compile step - so inside a
 * multiplatform module the annotations produced nothing and the build still passed. Metro is a Kotlin
 * compiler plugin and generates no Java, so a platform entry point can now sit in the module that
 * owns it, even after that module goes multiplatform (it would move to `androidMain`, which compiles
 * against the Android SDK like any other Android source set).
 *
 * Automation still reaches both through interfaces in `:core:interfaces` commonMain
 * (`LocationServiceController`, `ReminderScheduler`), so an iOS build can supply its own
 * implementations without touching plugin code. That part of the earlier design was right and is
 * kept.
 */
@DependencyGraph(AppScope::class)
internal interface AutomationMetroGraph {

    /** Fills the fields of the two entry points - the `@ContributesAndroidInjector` replacement. */
    val memberInjectors: Map<KClass<*>, MembersInjector<*>>

    @DependencyGraph.Factory
    fun interface Factory {

        @Suppress("LongParameterList")
        fun create(
            @Provides aapsLoggerRef: DeferredRef<AAPSLogger>,
            @Provides rhRef: DeferredRef<ResourceHelper>,
            @Provides configRef: DeferredRef<Config>,
            @Provides uiInteractionRef: DeferredRef<UiInteraction>,
            @Provides rxBusRef: DeferredRef<RxBus>,
            @Provides preferencesRef: DeferredRef<Preferences>,
            @Provides fabricPrivacyRef: DeferredRef<FabricPrivacy>,
            @Provides notificationHolderRef: DeferredRef<NotificationHolder>,
            @Provides lastLocationDataContainerRef: DeferredRef<LastLocationDataContainer>
        ): AutomationMetroGraph
    }

    @Provides fun aapsLogger(r: DeferredRef<AAPSLogger>): AAPSLogger = r.get()
    @Provides fun rh(r: DeferredRef<ResourceHelper>): ResourceHelper = r.get()
    @Provides fun config(r: DeferredRef<Config>): Config = r.get()
    @Provides fun uiInteraction(r: DeferredRef<UiInteraction>): UiInteraction = r.get()
    @Provides fun rxBus(r: DeferredRef<RxBus>): RxBus = r.get()
    @Provides fun preferences(r: DeferredRef<Preferences>): Preferences = r.get()
    @Provides fun fabricPrivacy(r: DeferredRef<FabricPrivacy>): FabricPrivacy = r.get()
    @Provides fun notificationHolder(r: DeferredRef<NotificationHolder>): NotificationHolder = r.get()
    @Provides fun lastLocationDataContainer(r: DeferredRef<LastLocationDataContainer>): LastLocationDataContainer = r.get()

    @Provides
    @IntoMap
    @ClassKey(TimerReminderReceiver::class)
    fun bindTimerReminderReceiver(injector: MembersInjector<TimerReminderReceiver>): MembersInjector<*> = injector

    @Provides
    @IntoMap
    @ClassKey(LocationService::class)
    fun bindLocationService(injector: MembersInjector<LocationService>): MembersInjector<*> = injector
}
