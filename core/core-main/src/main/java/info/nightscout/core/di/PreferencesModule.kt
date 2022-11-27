package info.nightscout.core.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.core.utils.CryptoUtil

@Module
@Suppress("unused")
abstract class PreferencesModule {

    @ContributesAndroidInjector abstract fun cryptoUtilInjector(): CryptoUtil
}