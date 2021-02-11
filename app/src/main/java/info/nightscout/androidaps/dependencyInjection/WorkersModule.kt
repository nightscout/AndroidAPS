package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.nsclient.NSClientWorker
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin
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
    @ContributesAndroidInjector abstract fun contributesNSProfileWorker(): NSProfilePlugin.NSProfileWorker
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorWorker(): SmsCommunicatorPlugin.SmsCommunicatorWorker
    @ContributesAndroidInjector abstract fun contributesNSClientWorker(): NSClientWorker
}