package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.Widget
import info.nightscout.androidaps.skins.SkinListPreference

@Module
@Suppress("unused")
abstract class UIModule {

    @ContributesAndroidInjector abstract fun skinListPreferenceInjector(): SkinListPreference
    @ContributesAndroidInjector abstract fun aapsWidgetInjector(): Widget
}