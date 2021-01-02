package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.openhumans.OHUploadWorker

@Module
@Suppress("unused")
abstract class OHUploaderModule {

    @ContributesAndroidInjector abstract fun contributesOHUploadWorkerInjector(): OHUploadWorker
}