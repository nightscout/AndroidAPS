package app.aaps.plugins.main.di

import app.aaps.plugins.main.general.actions.ActionsFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class ActionsModule {

    @ContributesAndroidInjector abstract fun contributesActionsFragment(): ActionsFragment
}