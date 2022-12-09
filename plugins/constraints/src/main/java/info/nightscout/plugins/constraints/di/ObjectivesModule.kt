package info.nightscout.plugins.constraints.di

import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class ObjectivesModule {

    @ContributesAndroidInjector abstract fun contributesObjectivesFragment(): info.nightscout.plugins.constraints.objectives.ObjectivesFragment
    @ContributesAndroidInjector abstract fun contributesObjectivesExamDialog(): info.nightscout.plugins.constraints.objectives.activities.ObjectivesExamDialog
    @ContributesAndroidInjector abstract fun contributesNtpProgressDialog(): info.nightscout.plugins.constraints.objectives.dialogs.NtpProgressDialog

    @ContributesAndroidInjector abstract fun objectiveInjector(): info.nightscout.plugins.constraints.objectives.objectives.Objective
    @ContributesAndroidInjector abstract fun objective0Injector(): info.nightscout.plugins.constraints.objectives.objectives.Objective0
    @ContributesAndroidInjector abstract fun objective1Injector(): info.nightscout.plugins.constraints.objectives.objectives.Objective1
    @ContributesAndroidInjector abstract fun objective2Injector(): info.nightscout.plugins.constraints.objectives.objectives.Objective2
    @ContributesAndroidInjector abstract fun objective3Injector(): info.nightscout.plugins.constraints.objectives.objectives.Objective3
    @ContributesAndroidInjector abstract fun objective4Injector(): info.nightscout.plugins.constraints.objectives.objectives.Objective4
    @ContributesAndroidInjector abstract fun objective5Injector(): info.nightscout.plugins.constraints.objectives.objectives.Objective5
    @ContributesAndroidInjector abstract fun objective6Injector(): info.nightscout.plugins.constraints.objectives.objectives.Objective6
    @ContributesAndroidInjector abstract fun objective7Injector(): info.nightscout.plugins.constraints.objectives.objectives.Objective7
    @ContributesAndroidInjector abstract fun objective9Injector(): info.nightscout.plugins.constraints.objectives.objectives.Objective9
    @ContributesAndroidInjector abstract fun objective10Injector(): info.nightscout.plugins.constraints.objectives.objectives.Objective10
}