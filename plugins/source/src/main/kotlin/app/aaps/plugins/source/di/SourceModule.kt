package app.aaps.plugins.source.di

import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.plugins.source.AidexPlugin
import app.aaps.plugins.source.BGSourceFragment
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.EversensePlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.MM640gPlugin
import app.aaps.plugins.source.NSClientSourcePlugin
import app.aaps.plugins.source.PathedOTAppPlugin
import app.aaps.plugins.source.PathedSIAppPlugin
import app.aaps.plugins.source.PathedSinoAppPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.source.activities.RequestDexcomPermissionActivity
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

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
    @ContributesAndroidInjector abstract fun contributesOTAppWorker(): PathedOTAppPlugin.PathedOTAppWorker
    @ContributesAndroidInjector abstract fun contributesSIAppWorker(): PathedSIAppPlugin.PathedSIAppWorker
    @ContributesAndroidInjector abstract fun contributesSinoAppWorker(): PathedSinoAppPlugin.PathedSinoAppWorker
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