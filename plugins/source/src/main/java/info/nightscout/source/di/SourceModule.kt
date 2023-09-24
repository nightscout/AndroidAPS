package info.nightscout.source.di

import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.source.XDripSource
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.source.AidexPlugin
import info.nightscout.source.BGSourceFragment
import info.nightscout.source.DexcomPlugin
import info.nightscout.source.EversensePlugin
import info.nightscout.source.GlimpPlugin
import info.nightscout.source.MM640gPlugin
import info.nightscout.source.NSClientSourcePlugin
import info.nightscout.source.PoctechPlugin
import info.nightscout.source.TomatoPlugin
import info.nightscout.source.XdripSourcePlugin
import info.nightscout.source.activities.RequestDexcomPermissionActivity

@Module(
    includes = [
        SourceModule.Bindings::class
    ]
)

@Suppress("unused")
abstract class SourceModule {

    @ContributesAndroidInjector abstract fun contributesBGSourceFragment(): BGSourceFragment

    @ContributesAndroidInjector abstract fun contributesXdripWorker(): XdripSourcePlugin.XdripSourceWorker
    @ContributesAndroidInjector abstract fun contributesDexcomWorker(): DexcomPlugin.DexcomWorker
    @ContributesAndroidInjector abstract fun contributesMM640gWorker(): MM640gPlugin.MM640gWorker
    @ContributesAndroidInjector abstract fun contributesGlimpWorker(): GlimpPlugin.GlimpWorker
    @ContributesAndroidInjector abstract fun contributesPoctechWorker(): PoctechPlugin.PoctechWorker
    @ContributesAndroidInjector abstract fun contributesTomatoWorker(): TomatoPlugin.TomatoWorker
    @ContributesAndroidInjector abstract fun contributesEversenseWorker(): EversensePlugin.EversenseWorker
    @ContributesAndroidInjector abstract fun contributesAidexWorker(): AidexPlugin.AidexWorker

    @ContributesAndroidInjector abstract fun contributesRequestDexcomPermissionActivity(): RequestDexcomPermissionActivity

    @Module
    interface Bindings {

        @Binds fun bindNSClientSource(nsClientSourcePlugin: NSClientSourcePlugin): NSClientSource
        @Binds fun bindDexcomBoyda(dexcomPlugin: DexcomPlugin): DexcomBoyda
        @Binds fun bindXDrip(xdripSourcePlugin: XdripSourcePlugin): XDripSource
    }
}