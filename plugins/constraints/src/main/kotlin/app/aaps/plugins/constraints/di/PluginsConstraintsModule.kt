package app.aaps.plugins.constraints.di

import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import dagger.Binds
import dagger.Module
import app.aaps.plugins.constraints.ConstraintsCheckerImpl
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.versionChecker.VersionCheckerUtilsImpl

@Module(
    includes = [
        PluginsConstraintsModule.Bindings::class,
        ObjectivesModule::class
    ]
)

@Suppress("unused")
abstract class PluginsConstraintsModule {

    @Module
    interface Bindings {

        @Binds fun bindVersionCheckerUtils(versionCheckerUtils: VersionCheckerUtilsImpl): VersionCheckerUtils
        @Binds fun bindBgQualityCheck(bgQualityCheck: BgQualityCheckPlugin): BgQualityCheck
        @Binds fun bindsConstraintChecker(constraintsCheckerImpl: ConstraintsCheckerImpl): ConstraintsChecker
        @Binds fun bindsObjectives(objectivesPlugin: ObjectivesPlugin): Objectives
    }
}