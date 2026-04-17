package app.aaps.ui.di

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.ui.search.SearchableProvider
import app.aaps.ui.activities.ErrorActivity
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import app.aaps.ui.compose.overview.graphs.GraphConfigRepositoryImpl

import app.aaps.ui.search.BuiltInSearchables
import app.aaps.ui.search.DialogSearchables
import app.aaps.ui.services.AlarmSoundService
import app.aaps.ui.widget.Widget
import app.aaps.ui.widget.WidgetConfigureActivity
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
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

        @Binds fun bindGraphConfigRepository(impl: GraphConfigRepositoryImpl): GraphConfigRepository

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

    @ContributesAndroidInjector abstract fun contributesAlarmSoundService(): AlarmSoundService

    @ContributesAndroidInjector abstract fun contributesWidget(): Widget
    @ContributesAndroidInjector abstract fun contributesWidgetConfigureActivity(): WidgetConfigureActivity

    @ContributesAndroidInjector abstract fun contributeErrorActivity(): ErrorActivity
}