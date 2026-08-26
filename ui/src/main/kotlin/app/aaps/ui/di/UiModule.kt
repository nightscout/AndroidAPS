package app.aaps.ui.di

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.ui.search.SearchableProvider
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import app.aaps.ui.compose.scenes.SceneIconResolverImpl
import app.aaps.ui.search.BuiltInSearchables
import app.aaps.ui.search.DialogSearchables
import app.aaps.ui.widget.WidgetUpdaterImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Provider
import javax.inject.Singleton

@Module(includes = [UiModule.Bindings::class])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class UiModule {

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindWidgetUpdater(impl: WidgetUpdaterImpl): WidgetUpdater

        // Scene icon resolution stays in :ui (the SceneIcons catalog lives here). The rest of the scene
        // engine + its DI bindings moved to :implementation (SceneModule).
        @Binds fun bindSceneIconResolver(impl: SceneIconResolverImpl): SceneIconResolver

        @Binds @IntoSet fun bindBuiltInSearchables(impl: BuiltInSearchables): SearchableProvider
        @Binds @IntoSet fun bindDialogSearchables(impl: DialogSearchables): SearchableProvider
    }

    companion object {

        // Provider<IobCobCalculator> breaks the Dagger cycle:
        // IobCobCalculator → CalculationWorkflow → OverviewDataCache → IobCobCalculator.
        // The lambda only resolves the calculator on demand (DB-observe paths).
        @Provides @Singleton fun provideLiveOverviewDataCache(
            factory: OverviewDataCacheFactory,
            iobCobCalculator: Provider<IobCobCalculator>,
            signals: CalculationSignals
        ): OverviewDataCache = factory.create({ iobCobCalculator.get() }, signals, observeDatabase = true)
    }

}