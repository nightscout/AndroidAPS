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
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * The shared-implementation wiring for **:wear only**. See [LoggerModule] for why `@InstallIn` is gone:
 * the phone builds all five of these in Metro now (`SharedImplBindings`), and wear keeps them here
 * through its own `includes`.
 */
@Module
@DisableInstallInCheck
open class SharedImplModule {

    @Provides
    @Singleton
    fun provideSP(context: Context): SP =
        SPImpl(defaultPreferences(context), context)

    @Provides
    @Singleton
    fun provideL(preferences: Lazy<Preferences>): L = LImpl { preferences.get() }

    @Provides
    @Singleton
    fun provideDateUtil(context: Context): DateUtil = DateUtilImpl(context)

    @Provides
    @Singleton
    fun provideRxBus(aapsLogger: AAPSLogger): RxBus = RxBusImpl(aapsLogger)

    @Provides
    @Singleton
    internal fun provideSchedulers(): AapsSchedulers = AapsSchedulersImpl()
}