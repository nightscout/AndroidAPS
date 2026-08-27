package app.aaps.wear.di

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.impl.logging.LImpl
import app.aaps.shared.impl.rx.AapsSchedulersImpl
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.shared.impl.sharedPreferences.SPImpl
import app.aaps.shared.impl.sharedPreferences.defaultPreferences
import app.aaps.shared.impl.utils.DateUtilImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The shared-implementation wiring for **:wear only**.
 *
 * The phone builds all five of these in Metro too, in its own `SharedImplBindings` - the classes are
 * shared, the wiring is not. Wear has its own graph, so it needs its own copy of these bindings.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object SharedImplModule {

    @Provides
    @SingleIn(AppScope::class)
    fun provideSP(context: Context): SP =
        SPImpl(defaultPreferences(context), context)

    /**
     * Deferred on purpose: `Preferences` is `PreferencesImpl`, which needs `L` to log, so asking for it
     * directly here would be a cycle. `Provider` is Metro's `dagger.Lazy` - it just defers the lookup.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideL(preferences: Provider<Preferences>): L = LImpl { preferences() }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDateUtil(context: Context): DateUtil = DateUtilImpl(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideRxBus(aapsLogger: AAPSLogger): RxBus = RxBusImpl(aapsLogger)

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideSchedulers(): AapsSchedulers = AapsSchedulersImpl()
}
