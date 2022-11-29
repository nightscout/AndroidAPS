package info.nightscout.pump.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.diaconn.api.DiaconnLogUploader

@Module
@Suppress("unused")
abstract class DiaconnLogUploaderModule {
    @ContributesAndroidInjector abstract fun contributesDiaconnLogUploader(): DiaconnLogUploader
}