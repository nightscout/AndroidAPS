package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.general.smsCommunicator.AuthRequest
import info.nightscout.plugins.general.smsCommunicator.SmsCommunicatorFragment
import info.nightscout.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.plugins.general.smsCommunicator.activities.SmsCommunicatorOtpActivity

@Module
@Suppress("unused")
abstract class SMSCommunicatorModule {

    @ContributesAndroidInjector abstract fun authRequestInjector(): AuthRequest
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorOtpActivity(): SmsCommunicatorOtpActivity
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorFragment(): SmsCommunicatorFragment
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorWorker(): SmsCommunicatorPlugin.SmsCommunicatorWorker
}