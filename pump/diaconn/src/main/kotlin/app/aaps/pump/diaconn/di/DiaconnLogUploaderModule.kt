package app.aaps.pump.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.pump.diaconn.api.DiaconnLogUploader

@Module
@Suppress("unused")
interface DiaconnLogUploaderModule {
    @ContributesAndroidInjector fun contributesDiaconnLogUploader(): DiaconnLogUploader
}