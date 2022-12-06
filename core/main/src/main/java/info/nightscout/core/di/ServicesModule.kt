package info.nightscout.core.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.core.services.AlarmSoundService

@Module
@Suppress("unused")
abstract class ServicesModule {

    @ContributesAndroidInjector abstract fun contributesAlarmSoundService(): AlarmSoundService
}