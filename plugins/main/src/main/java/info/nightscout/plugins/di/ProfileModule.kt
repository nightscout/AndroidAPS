package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.profile.ProfileFragment
import info.nightscout.plugins.profile.ProfilePlugin

@Module
@Suppress("unused")
abstract class ProfileModule {

    @ContributesAndroidInjector abstract fun contributesNSProfileWorker(): ProfilePlugin.NSProfileWorker
    @ContributesAndroidInjector abstract fun contributesLocalProfileFragment(): ProfileFragment
}