package app.aaps.pump.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.pump.diaconn.service.DiaconnG8Service

@Module
@Suppress("unused")
interface DiaconnG8ServiceModule {
    @ContributesAndroidInjector fun contributesDiaconnG8Service(): DiaconnG8Service
}