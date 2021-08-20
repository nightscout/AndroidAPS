package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import info.nightscout.androidaps.plugins.general.maintenance.formats.ClassicPrefsFormat
import info.nightscout.androidaps.plugins.general.maintenance.formats.EncryptedPrefsFormat
import info.nightscout.androidaps.utils.CryptoUtil

@Module
@Suppress("unused")
abstract class PreferencesModule {

    @ContributesAndroidInjector abstract fun cryptoUtilInjector(): CryptoUtil
    @ContributesAndroidInjector abstract fun encryptedPrefsFormatInjector(): EncryptedPrefsFormat
    @ContributesAndroidInjector abstract fun classicPrefsFormatInjector(): ClassicPrefsFormat
    @ContributesAndroidInjector abstract fun prefImportListProviderInjector(): PrefFileListProvider
}