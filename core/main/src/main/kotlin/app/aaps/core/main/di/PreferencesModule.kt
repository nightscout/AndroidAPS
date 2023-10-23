package app.aaps.core.main.di

import app.aaps.core.main.utils.CryptoUtil
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class PreferencesModule {

    @ContributesAndroidInjector abstract fun cryptoUtilInjector(): CryptoUtil
}