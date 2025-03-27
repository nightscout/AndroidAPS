package app.aaps.shared.impl.di

import android.content.Context
import androidx.preference.PreferenceManager
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
import app.aaps.shared.impl.utils.DateUtilImpl
import dagger.Lazy
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(
    includes = [
    ]
)
open class SharedImplModule {

    @Provides
    @Singleton
    fun provideSP(context: Context): SP = SPImpl(PreferenceManager.getDefaultSharedPreferences(context), context)

    @Provides
    @Singleton
    fun provideL(preferences: Lazy<Preferences>): L = LImpl(preferences)

    @Provides
    @Singleton
    fun provideDateUtil(context: Context): DateUtil = DateUtilImpl(context)

    @Provides
    @Singleton
    fun provideRxBus(aapsSchedulers: AapsSchedulers, aapsLogger: AAPSLogger): RxBus = RxBusImpl(aapsSchedulers, aapsLogger)

    @Provides
    @Singleton
    internal fun provideSchedulers(): AapsSchedulers = AapsSchedulersImpl()
}