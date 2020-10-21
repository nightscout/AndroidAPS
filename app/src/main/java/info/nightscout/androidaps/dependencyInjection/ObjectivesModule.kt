package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*

@Module
@Suppress("unused")
abstract class ObjectivesModule {

    @ContributesAndroidInjector abstract fun objectiveInjector(): Objective
    @ContributesAndroidInjector abstract fun objective0Injector(): Objective0
    @ContributesAndroidInjector abstract fun objective1Injector(): Objective1
    @ContributesAndroidInjector abstract fun objective2Injector(): Objective2
    @ContributesAndroidInjector abstract fun objective3Injector(): Objective3
    @ContributesAndroidInjector abstract fun objective4Injector(): Objective4
    @ContributesAndroidInjector abstract fun objective5Injector(): Objective5
    @ContributesAndroidInjector abstract fun objective6Injector(): Objective6
    @ContributesAndroidInjector abstract fun objective7Injector(): Objective7
    @ContributesAndroidInjector abstract fun objective8Injector(): Objective8
    @ContributesAndroidInjector abstract fun objective9Injector(): Objective9
    @ContributesAndroidInjector abstract fun objective10Injector(): Objective10

}