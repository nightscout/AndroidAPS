package app.aaps.plugins.sync.di

import app.aaps.plugins.sync.smsCommunicator.AuthRequest
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.sync.smsCommunicator.activities.SmsCommunicatorOtpActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class SMSCommunicatorModule {

    @ContributesAndroidInjector abstract fun authRequestInjector(): AuthRequest
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorOtpActivity(): SmsCommunicatorOtpActivity
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorWorker(): SmsCommunicatorPlugin.SmsCommunicatorWorker
}
