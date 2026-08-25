package app.aaps.pump.diaconn.di

import app.aaps.pump.diaconn.api.DiaconnLogUploader
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
interface DiaconnLogUploaderModule {
    @ContributesAndroidInjector fun contributesDiaconnLogUploader(): DiaconnLogUploader
}