package app.aaps.di

import app.aaps.receivers.AutoStartReceiver
import app.aaps.receivers.DataReceiver
import app.aaps.receivers.SmsReceiver
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ReceiversModule {

    @ContributesAndroidInjector abstract fun contributesAutoStartReceiver(): AutoStartReceiver
    @ContributesAndroidInjector abstract fun contributesDataReceiver(): DataReceiver
    @ContributesAndroidInjector abstract fun contributesSmsReceiver(): SmsReceiver
}
