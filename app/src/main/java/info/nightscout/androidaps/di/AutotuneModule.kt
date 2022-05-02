package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.autotune.AutotuneCore
import info.nightscout.androidaps.plugins.general.autotune.AutotuneIob
import info.nightscout.androidaps.plugins.general.autotune.AutotunePrep
import info.nightscout.androidaps.plugins.general.autotune.AutotuneFS
import info.nightscout.androidaps.plugins.general.autotune.data.*

@Module
@Suppress("unused")
abstract class AutotuneModule {
    @ContributesAndroidInjector abstract fun autoTunePrepInjector(): AutotunePrep
    @ContributesAndroidInjector abstract fun autoTuneIobInjector(): AutotuneIob
    @ContributesAndroidInjector abstract fun autoTuneCoreInjector(): AutotuneCore
    @ContributesAndroidInjector abstract fun autoTuneFSInjector(): AutotuneFS

    @ContributesAndroidInjector abstract fun autoTuneATProfileInjector(): ATProfile
    @ContributesAndroidInjector abstract fun autoTuneBGDatumInjector(): BGDatum
    @ContributesAndroidInjector abstract fun autoTuneCRDatumInjector(): CRDatum
    @ContributesAndroidInjector abstract fun autoTunePreppedGlucoseInjector(): PreppedGlucose
}