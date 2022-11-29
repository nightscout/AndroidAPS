package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.general.actions.ActionsFragment

@Module
@Suppress("unused")
abstract class ActionsModule {

    @ContributesAndroidInjector abstract fun contributesActionsFragment(): ActionsFragment
}