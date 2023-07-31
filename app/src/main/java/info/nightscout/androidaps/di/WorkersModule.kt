package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.food.FoodPlugin
import info.nightscout.androidaps.plugins.general.maintenance.ImportExportPrefsImpl
import info.nightscout.androidaps.plugins.general.nsclient.NSClientAddAckWorker
import info.nightscout.androidaps.plugins.general.nsclient.NSClientAddUpdateWorker
import info.nightscout.androidaps.plugins.general.nsclient.NSClientMbgWorker
import info.nightscout.androidaps.plugins.general.nsclient.NSClientUpdateRemoveAckWorker
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.source.*

@Module
@Suppress("unused")
abstract class WorkersModule {

    @ContributesAndroidInjector abstract fun contributesXdripWorker(): XdripPlugin.XdripWorker
    @ContributesAndroidInjector abstract fun contributesDexcomWorker(): DexcomPlugin.DexcomWorker
    @ContributesAndroidInjector abstract fun contributesMM640gWorker(): MM640gPlugin.MM640gWorker
    @ContributesAndroidInjector abstract fun contributesGlimpWorker(): GlimpPlugin.GlimpWorker
    @ContributesAndroidInjector abstract fun contributesPoctechWorker(): PoctechPlugin.PoctechWorker
    @ContributesAndroidInjector abstract fun contributesTomatoWorker(): TomatoPlugin.TomatoWorker
    @ContributesAndroidInjector abstract fun contributesEversenseWorker(): EversensePlugin.EversenseWorker
    @ContributesAndroidInjector abstract fun contributesNSClientSourceWorker(): NSClientSourcePlugin.NSClientSourceWorker
    @ContributesAndroidInjector abstract fun contributesNSProfileWorker(): LocalProfilePlugin.NSProfileWorker
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorWorker(): SmsCommunicatorPlugin.SmsCommunicatorWorker
    @ContributesAndroidInjector abstract fun contributesNSClientWorker(): NSClientAddUpdateWorker
    @ContributesAndroidInjector abstract fun contributesNSClientAddAckWorker(): NSClientAddAckWorker
    @ContributesAndroidInjector abstract fun contributesNSClientUpdateRemoveAckWorker(): NSClientUpdateRemoveAckWorker
    @ContributesAndroidInjector abstract fun contributesNSClientMbgWorker(): NSClientMbgWorker
    @ContributesAndroidInjector abstract fun contributesFoodWorker(): FoodPlugin.FoodWorker
    @ContributesAndroidInjector abstract fun contributesCsvExportWorker(): ImportExportPrefsImpl.CsvExportWorker
    @ContributesAndroidInjector abstract fun contributesAidexWorker(): AidexPlugin.AidexWorker
    @ContributesAndroidInjector abstract fun contributesPatchedSIAppWorker(): PathedSIAppPlugin.PathedSIAppWorker
    @ContributesAndroidInjector abstract fun contributesPatchedSinoAppWorker(): PathedSinoAppPlugin.PathedSinoAppWorker
}