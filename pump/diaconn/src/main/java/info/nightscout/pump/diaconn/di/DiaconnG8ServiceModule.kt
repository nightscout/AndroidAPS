package info.nightscout.pump.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.diaconn.service.DiaconnG8Service

@Module
@Suppress("unused")
abstract class DiaconnG8ServiceModule {
    @ContributesAndroidInjector abstract fun contributesDiaconnG8Service(): DiaconnG8Service
}