package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.profile.ProfileFragment

@Module
@Suppress("unused")
abstract class ProfileModule {

    @ContributesAndroidInjector abstract fun contributesLocalProfileFragment(): ProfileFragment
}