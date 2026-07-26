package app.aaps.pump.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.aaps.pump.diaconn.service.DiaconnG8Service

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
interface DiaconnG8ServiceModule {
    @ContributesAndroidInjector fun contributesDiaconnG8Service(): DiaconnG8Service
}