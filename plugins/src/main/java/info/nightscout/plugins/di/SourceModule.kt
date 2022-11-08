package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.source.BGSourceFragment
import info.nightscout.plugins.source.NSClientSourcePlugin

@Module
@Suppress("unused")
abstract class SourceModule {

    @ContributesAndroidInjector abstract fun contributesBGSourceFragment(): BGSourceFragment

    @ContributesAndroidInjector abstract fun contributesNSClientSourceWorker(): NSClientSourcePlugin.NSClientSourceWorker
}