package app.aaps.plugins.constraints.di

import app.aaps.plugins.constraints.objectives.ObjectivesFragment
import app.aaps.plugins.constraints.objectives.activities.ObjectivesExamDialog
import app.aaps.plugins.constraints.objectives.dialogs.NtpProgressDialog
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.objectives.objectives.Objective0
import app.aaps.plugins.constraints.objectives.objectives.Objective1
import app.aaps.plugins.constraints.objectives.objectives.Objective2
import app.aaps.plugins.constraints.objectives.objectives.Objective3
import app.aaps.plugins.constraints.objectives.objectives.Objective4
import app.aaps.plugins.constraints.objectives.objectives.Objective5
import app.aaps.plugins.constraints.objectives.objectives.Objective6
import app.aaps.plugins.constraints.objectives.objectives.Objective7
import app.aaps.plugins.constraints.objectives.objectives.Objective8
import app.aaps.plugins.constraints.objectives.objectives.Objective9
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Qualifier

@Module(
    includes = [
        ObjectivesModule.Provide::class,
        ObjectivesModule.ObjectivesListModule::class
    ]
)
@Suppress("unused")
abstract class ObjectivesModule {

    @ContributesAndroidInjector abstract fun contributesObjectivesFragment(): ObjectivesFragment
    @ContributesAndroidInjector abstract fun contributesObjectivesExamDialog(): ObjectivesExamDialog
    @ContributesAndroidInjector abstract fun contributesNtpProgressDialog(): NtpProgressDialog

    @Module
    open class Provide {

        @Provides
        fun providesObjectives(@ObjectivesListModule.ObjectiveClass allObjectives: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards Objective>)
            : List<@JvmSuppressWildcards Objective> = allObjectives.toList().sortedBy { it.first }.map { it.second }
    }

    @Module
    interface ObjectivesListModule {

        @Binds
        @ObjectiveClass
        @IntoMap
        @IntKey(0)
        fun bindObjective0(objective: Objective0): Objective

        @Binds
        @ObjectiveClass
        @IntoMap
        @IntKey(1) fun bindObjective1(objective: Objective1): Objective

        @Binds
        @ObjectiveClass
        @IntoMap
        @IntKey(2) fun bindObjective2(objective: Objective2): Objective

        @Binds
        @ObjectiveClass
        @IntoMap
        @IntKey(3) fun bindObjective3(objective: Objective3): Objective

        @Binds
        @ObjectiveClass
        @IntoMap
        @IntKey(4) fun bindObjective4(objective: Objective4): Objective

        @Binds
        @ObjectiveClass
        @IntoMap
        @IntKey(5) fun bindObjective5(objective: Objective5): Objective

        @Binds
        @ObjectiveClass
        @IntoMap
        @IntKey(6) fun bindObjective6(objective: Objective6): Objective

        @Binds
        @ObjectiveClass
        @IntoMap
        @IntKey(7) fun bindObjective7(objective: Objective7): Objective

        @Binds
        @ObjectiveClass
        @IntoMap
        @IntKey(8) fun bindObjective8(objective: Objective8): Objective

        @Binds
        @ObjectiveClass
        @IntoMap
        @IntKey(9) fun bindObjective9(objective: Objective9): Objective

        @Qualifier
        annotation class ObjectiveClass
    }
}