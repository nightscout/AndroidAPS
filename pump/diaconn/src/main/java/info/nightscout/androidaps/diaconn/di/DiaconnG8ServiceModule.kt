package info.nightscout.androidaps.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.diaconn.service.DiaconnG8Service

@Module
@Suppress("unused")
abstract class DiaconnG8ServiceModule {
    @ContributesAndroidInjector abstract fun contributesDiaconnG8Service(): DiaconnG8Service
}