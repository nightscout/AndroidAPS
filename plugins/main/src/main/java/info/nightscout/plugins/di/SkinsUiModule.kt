package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.skins.SkinListPreference

@Module
@Suppress("unused")
abstract class SkinsUiModule {

    @ContributesAndroidInjector abstract fun skinListPreferenceInjector(): SkinListPreference
}