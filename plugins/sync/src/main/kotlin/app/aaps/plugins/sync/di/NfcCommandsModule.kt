package app.aaps.plugins.sync.di

import app.aaps.plugins.sync.nfcCommands.NfcControlActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class NfcCommandsModule {
    @ContributesAndroidInjector abstract fun contributesNfcControlActivity(): NfcControlActivity
}
