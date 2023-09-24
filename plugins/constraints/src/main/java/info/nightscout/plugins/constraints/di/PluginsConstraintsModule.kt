package info.nightscout.plugins.constraints.di

import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import dagger.Binds
import dagger.Module
import info.nightscout.plugins.constraints.ConstraintsCheckerImpl
import info.nightscout.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import info.nightscout.plugins.constraints.versionChecker.VersionCheckerUtilsImpl

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
    }
}