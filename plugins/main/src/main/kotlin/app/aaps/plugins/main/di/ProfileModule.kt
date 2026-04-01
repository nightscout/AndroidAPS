package app.aaps.plugins.main.di

import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.plugins.main.profile.ProfileFragment
import app.aaps.plugins.main.profile.ProfilePlugin
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ProfileModule {

    @ContributesAndroidInjector abstract fun contributesLocalProfileFragment(): ProfileFragment

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindProfileSource(profilePlugin: ProfilePlugin): ProfileSource
    }
}