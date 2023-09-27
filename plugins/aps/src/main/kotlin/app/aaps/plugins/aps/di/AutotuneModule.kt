package app.aaps.plugins.aps.di

import app.aaps.plugins.aps.autotune.AutotuneCore
import app.aaps.plugins.aps.autotune.AutotuneFS
import app.aaps.plugins.aps.autotune.AutotuneFragment
import app.aaps.plugins.aps.autotune.AutotuneIob
import app.aaps.plugins.aps.autotune.AutotunePrep
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.autotune.data.BGDatum
import app.aaps.plugins.aps.autotune.data.CRDatum
import app.aaps.plugins.aps.autotune.data.PreppedGlucose
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class AutotuneModule {

    @ContributesAndroidInjector abstract fun contributesAutotuneFragment(): AutotuneFragment

    @ContributesAndroidInjector abstract fun autoTunePrepInjector(): AutotunePrep
    @ContributesAndroidInjector abstract fun autoTuneIobInjector(): AutotuneIob
    @ContributesAndroidInjector abstract fun autoTuneCoreInjector(): AutotuneCore
    @ContributesAndroidInjector abstract fun autoTuneFSInjector(): AutotuneFS

    @ContributesAndroidInjector abstract fun autoTuneATProfileInjector(): ATProfile
    @ContributesAndroidInjector abstract fun autoTuneBGDatumInjector(): BGDatum
    @ContributesAndroidInjector abstract fun autoTuneCRDatumInjector(): CRDatum
    @ContributesAndroidInjector abstract fun autoTunePreppedGlucoseInjector(): PreppedGlucose
}