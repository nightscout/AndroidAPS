package info.nightscout.shared.impl.di

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.interfaces.L
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.impl.logging.AAPSLoggerProduction
import info.nightscout.shared.impl.logging.LImpl
import info.nightscout.shared.impl.rx.AapsSchedulersImpl
import info.nightscout.shared.impl.rx.bus.RxBusImpl
import info.nightscout.shared.impl.sharedPreferences.SPImplementation
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.DateUtilImpl
import javax.inject.Singleton

@Module(
    includes = [
    ]
)
open class SharedImplModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SP = SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), context)

    @Provides
    @Singleton
    fun provideL(sp: SP): L = LImpl(sp)

    @Provides
    @Singleton
    fun provideDateUtil(context: Context): DateUtil = DateUtilImpl(context)

    @Provides
    @Singleton
    fun provideAAPSLogger(l: L): AAPSLogger = AAPSLoggerProduction(l)

    @Provides
    @Singleton
    fun provideRxBus(aapsSchedulers: AapsSchedulers, aapsLogger: AAPSLogger): RxBus = RxBusImpl(aapsSchedulers, aapsLogger)

    @Provides
    @Singleton
    internal fun provideSchedulers(): AapsSchedulers = AapsSchedulersImpl()
}