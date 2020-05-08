package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.autotune.FS
import info.nightscout.androidaps.plugins.general.autotune.data.*
import info.nightscout.androidaps.plugins.general.autotune.AutotunePrep.*

@Module
@Suppress("unused")
abstract class AutotuneModule {
    @ContributesAndroidInjector abstract fun autoTuneBGDatumInjector(): BGDatum
    @ContributesAndroidInjector abstract fun autoTuneCRDatumInjector(): CRDatum
    @ContributesAndroidInjector abstract fun autoTuneIobInputsInjector(): IobInputs
    @ContributesAndroidInjector abstract fun autoTuneNsTreatmentInjector(): NsTreatment
    @ContributesAndroidInjector abstract fun autoTuneOptsInjector(): Opts
    @ContributesAndroidInjector abstract fun autoTunePrepOutputInjector(): PrepOutput
    @ContributesAndroidInjector abstract fun autoTuneTunedProfileInjector(): TunedProfile
    @ContributesAndroidInjector abstract fun autoTuneMealInjector(): Meal
    @ContributesAndroidInjector abstract fun autoTunePrepInjector(): AutotunePrep
    @ContributesAndroidInjector abstract fun autoTuneIobInjector(): Iob

    @ContributesAndroidInjector abstract fun autoTuneFSInjector(): FS
}