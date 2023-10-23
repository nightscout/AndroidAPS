package app.aaps.plugins.main.di

import app.aaps.plugins.main.skins.SkinListPreference
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class SkinsUiModule {

    @ContributesAndroidInjector abstract fun skinListPreferenceInjector(): SkinListPreference
}