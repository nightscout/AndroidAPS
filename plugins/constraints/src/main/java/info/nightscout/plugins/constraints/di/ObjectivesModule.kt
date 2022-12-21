package info.nightscout.plugins.constraints.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.constraints.objectives.ObjectivesFragment
import info.nightscout.plugins.constraints.objectives.activities.ObjectivesExamDialog
import info.nightscout.plugins.constraints.objectives.dialogs.NtpProgressDialog
import info.nightscout.plugins.constraints.objectives.objectives.Objective
import info.nightscout.plugins.constraints.objectives.objectives.Objective0
import info.nightscout.plugins.constraints.objectives.objectives.Objective1
import info.nightscout.plugins.constraints.objectives.objectives.Objective10
import info.nightscout.plugins.constraints.objectives.objectives.Objective2
import info.nightscout.plugins.constraints.objectives.objectives.Objective3
import info.nightscout.plugins.constraints.objectives.objectives.Objective4
import info.nightscout.plugins.constraints.objectives.objectives.Objective5
import info.nightscout.plugins.constraints.objectives.objectives.Objective6
import info.nightscout.plugins.constraints.objectives.objectives.Objective7
import info.nightscout.plugins.constraints.objectives.objectives.Objective9

@Module
@Suppress("unused")
abstract class ObjectivesModule {

    @ContributesAndroidInjector abstract fun contributesObjectivesFragment(): ObjectivesFragment
    @ContributesAndroidInjector abstract fun contributesObjectivesExamDialog(): ObjectivesExamDialog
    @ContributesAndroidInjector abstract fun contributesNtpProgressDialog(): NtpProgressDialog

    @ContributesAndroidInjector abstract fun objectiveInjector(): Objective
    @ContributesAndroidInjector abstract fun objective0Injector(): Objective0
    @ContributesAndroidInjector abstract fun objective1Injector(): Objective1
    @ContributesAndroidInjector abstract fun objective2Injector(): Objective2
    @ContributesAndroidInjector abstract fun objective3Injector(): Objective3
    @ContributesAndroidInjector abstract fun objective4Injector(): Objective4
    @ContributesAndroidInjector abstract fun objective5Injector(): Objective5
    @ContributesAndroidInjector abstract fun objective6Injector(): Objective6
    @ContributesAndroidInjector abstract fun objective7Injector(): Objective7
    @ContributesAndroidInjector abstract fun objective9Injector(): Objective9
    @ContributesAndroidInjector abstract fun objective10Injector(): Objective10
}