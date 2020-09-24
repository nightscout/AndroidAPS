package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.skins.SkinListPreference

@Module
@Suppress("unused")
abstract class UIModule {
    @ContributesAndroidInjector abstract fun skinListPreferenceInjector(): SkinListPreference
}