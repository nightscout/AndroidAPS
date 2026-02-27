package app.aaps.plugins.main.di

import app.aaps.plugins.main.general.smsCommunicator.AuthRequest
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorFragment
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.main.general.smsCommunicator.activities.SmsCommunicatorOtpActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class SMSCommunicatorModule {

    @ContributesAndroidInjector abstract fun authRequestInjector(): AuthRequest
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorOtpActivity(): SmsCommunicatorOtpActivity
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorFragment(): SmsCommunicatorFragment
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorWorker(): SmsCommunicatorPlugin.SmsCommunicatorWorker
}