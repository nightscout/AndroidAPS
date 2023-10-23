package app.aaps.plugins.main.di

import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.plugins.main.profile.ProfileFragment
import app.aaps.plugins.main.profile.ProfilePlugin
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class ProfileModule {

    @ContributesAndroidInjector abstract fun contributesLocalProfileFragment(): ProfileFragment

    @Module
    interface Bindings {

        @Binds fun bindProfileSource(profilePlugin: ProfilePlugin): ProfileSource
    }

}