package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.autotune.FS
import info.nightscout.androidaps.plugins.general.autotune.data.*
import info.nightscout.androidaps.plugins.general.autotune.AutotunePrep.*

@Module
@Suppress("unused")
abstract class AutotuneModule {
    @ContributesAndroidInjector abstract fun autoTuneOptsInjector(): Opts
    @ContributesAndroidInjector abstract fun autoTuneFSInjector(): FS
    @ContributesAndroidInjector abstract fun autoTunePrepInjector(): Prep
}