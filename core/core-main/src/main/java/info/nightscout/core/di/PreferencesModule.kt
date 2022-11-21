package info.nightscout.core.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.maintenance.formats.EncryptedPrefsFormat
import info.nightscout.core.utils.CryptoUtil
import info.nightscout.interfaces.maintenance.PrefFileListProvider

@Module
@Suppress("unused")
abstract class PreferencesModule {

    @ContributesAndroidInjector abstract fun cryptoUtilInjector(): CryptoUtil
    @ContributesAndroidInjector abstract fun encryptedPrefsFormatInjector(): EncryptedPrefsFormat
    @ContributesAndroidInjector abstract fun prefImportListProviderInjector(): PrefFileListProvider
}