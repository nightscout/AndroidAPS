package info.nightscout.plugins.aps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.general.autotune.data.PreppedGlucose

@Module
@Suppress("unused")
abstract class AutotuneModule {

    @ContributesAndroidInjector abstract fun contributesAutotuneFragment(): info.nightscout.plugins.general.autotune.AutotuneFragment

    @ContributesAndroidInjector abstract fun autoTunePrepInjector(): info.nightscout.plugins.general.autotune.AutotunePrep
    @ContributesAndroidInjector abstract fun autoTuneIobInjector(): info.nightscout.plugins.general.autotune.AutotuneIob
    @ContributesAndroidInjector abstract fun autoTuneCoreInjector(): info.nightscout.plugins.general.autotune.AutotuneCore
    @ContributesAndroidInjector abstract fun autoTuneFSInjector(): info.nightscout.plugins.general.autotune.AutotuneFS

    @ContributesAndroidInjector abstract fun autoTuneATProfileInjector(): info.nightscout.plugins.general.autotune.data.ATProfile
    @ContributesAndroidInjector abstract fun autoTuneBGDatumInjector(): info.nightscout.plugins.general.autotune.data.BGDatum
    @ContributesAndroidInjector abstract fun autoTuneCRDatumInjector(): info.nightscout.plugins.general.autotune.data.CRDatum
    @ContributesAndroidInjector abstract fun autoTunePreppedGlucoseInjector(): PreppedGlucose
}