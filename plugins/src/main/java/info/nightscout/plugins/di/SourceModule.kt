package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.profile.ProfilePlugin
import info.nightscout.plugins.source.AidexPlugin
import info.nightscout.plugins.source.BGSourceFragment
import info.nightscout.plugins.source.DexcomPlugin
import info.nightscout.plugins.source.EversensePlugin
import info.nightscout.plugins.source.GlimpPlugin
import info.nightscout.plugins.source.MM640gPlugin
import info.nightscout.plugins.source.NSClientSourcePlugin
import info.nightscout.plugins.source.PoctechPlugin
import info.nightscout.plugins.source.TomatoPlugin
import info.nightscout.plugins.source.XdripPlugin
import info.nightscout.plugins.source.activities.RequestDexcomPermissionActivity

@Module
@Suppress("unused")
abstract class SourceModule {

    @ContributesAndroidInjector abstract fun contributesBGSourceFragment(): BGSourceFragment

    @ContributesAndroidInjector abstract fun contributesNSProfileWorker(): ProfilePlugin.NSProfileWorker
    @ContributesAndroidInjector abstract fun contributesNSClientSourceWorker(): NSClientSourcePlugin.NSClientSourceWorker
    @ContributesAndroidInjector abstract fun contributesXdripWorker(): XdripPlugin.XdripWorker
    @ContributesAndroidInjector abstract fun contributesDexcomWorker(): DexcomPlugin.DexcomWorker
    @ContributesAndroidInjector abstract fun contributesMM640gWorker(): MM640gPlugin.MM640gWorker
    @ContributesAndroidInjector abstract fun contributesGlimpWorker(): GlimpPlugin.GlimpWorker
    @ContributesAndroidInjector abstract fun contributesPoctechWorker(): PoctechPlugin.PoctechWorker
    @ContributesAndroidInjector abstract fun contributesTomatoWorker(): TomatoPlugin.TomatoWorker
    @ContributesAndroidInjector abstract fun contributesEversenseWorker(): EversensePlugin.EversenseWorker
    @ContributesAndroidInjector abstract fun contributesAidexWorker(): AidexPlugin.AidexWorker

    @ContributesAndroidInjector abstract fun contributesRequestDexcomPermissionActivity(): RequestDexcomPermissionActivity
}