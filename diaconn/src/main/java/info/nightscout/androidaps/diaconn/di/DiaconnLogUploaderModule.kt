package info.nightscout.androidaps.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.diaconn.api.DiaconnLogUploader
import info.nightscout.androidaps.diaconn.service.DiaconnG8Service

@Module
@Suppress("unused")
abstract class DiaconnLogUploaderModule {
    @ContributesAndroidInjector abstract fun contributesDiaconnLogUploader(): DiaconnLogUploader
}