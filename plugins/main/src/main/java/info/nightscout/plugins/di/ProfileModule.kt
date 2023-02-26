package info.nightscout.plugins.di

import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.interfaces.profile.ProfileSource
import info.nightscout.plugins.profile.ProfileFragment
import info.nightscout.plugins.profile.ProfilePlugin

@Module
@Suppress("unused")
abstract class ProfileModule {

    @ContributesAndroidInjector abstract fun contributesLocalProfileFragment(): ProfileFragment

    @Module
    interface Bindings {

        @Binds fun bindProfileSource(profilePlugin: ProfilePlugin): ProfileSource
    }

}