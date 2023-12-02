package app.aaps.plugins.constraints.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.plugins.constraints.objectives.ObjectivesFragment
import app.aaps.plugins.constraints.objectives.activities.ObjectivesExamDialog
import app.aaps.plugins.constraints.objectives.dialogs.NtpProgressDialog
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.objectives.objectives.Objective0
import app.aaps.plugins.constraints.objectives.objectives.Objective1
import app.aaps.plugins.constraints.objectives.objectives.Objective10
import app.aaps.plugins.constraints.objectives.objectives.Objective11
import app.aaps.plugins.constraints.objectives.objectives.Objective2
import app.aaps.plugins.constraints.objectives.objectives.Objective3
import app.aaps.plugins.constraints.objectives.objectives.Objective4
import app.aaps.plugins.constraints.objectives.objectives.Objective5
import app.aaps.plugins.constraints.objectives.objectives.Objective6
import app.aaps.plugins.constraints.objectives.objectives.Objective7
import app.aaps.plugins.constraints.objectives.objectives.Objective9

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
    @ContributesAndroidInjector abstract fun objective11Injector(): Objective11
}