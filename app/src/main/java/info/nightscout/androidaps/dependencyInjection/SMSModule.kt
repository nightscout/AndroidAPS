package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.smsCommunicator.AuthRequest

@Module
@Suppress("unused")
abstract class SMSModule {

    @ContributesAndroidInjector abstract fun authRequestInjector(): AuthRequest
}