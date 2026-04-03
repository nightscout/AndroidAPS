package app.aaps.pump.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.aaps.pump.diaconn.api.DiaconnLogUploader

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
interface DiaconnLogUploaderModule {
    @ContributesAndroidInjector fun contributesDiaconnLogUploader(): DiaconnLogUploader
}