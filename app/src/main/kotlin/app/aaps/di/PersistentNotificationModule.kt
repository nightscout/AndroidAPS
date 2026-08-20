package app.aaps.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.ui.CarbSuggestionActions
import app.aaps.persistentNotification.DummyService
import app.aaps.persistentNotification.PersistentNotificationPlugin
import app.aaps.receivers.CarbSuggestionActionsImpl
import app.aaps.receivers.CarbSuggestionReceiver
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * Wiring for the persistent foreground notification, which lives in :app rather than in
 * :plugins:main.
 *
 * The three classes moved here so that :plugins:main can become multiplatform. `DummyService` field
 * injects, and a members injector is generated code - see `_docs/KMP_IOS_FEASIBILITY.md`, under
 * "Decisions taken". Unlike the other `di/` files, this one is not a lift: the classes really are in
 * :app now, so they keep their own `@Inject constructor` and only their bindings are here.
 *
 * Keeps @IntKey 0, unchanged by the move. See PluginsListModule for the overall ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class PersistentNotificationModule {

    @ContributesAndroidInjector abstract fun contributesDummyService(): DummyService

    @ContributesAndroidInjector abstract fun contributesCarbSuggestionReceiver(): CarbSuggestionReceiver

    /** LoopPlugin asks for the ignore-carbs intents through this rather than naming the receiver. */
    @Binds abstract fun bindCarbSuggestionActions(impl: CarbSuggestionActionsImpl): CarbSuggestionActions

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(0)
    abstract fun bindPersistentNotificationPlugin(plugin: PersistentNotificationPlugin): PluginBase
}
