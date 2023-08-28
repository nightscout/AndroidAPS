package info.nightscout.plugins.constraints.di

import dagger.Binds
import dagger.Module
import info.nightscout.interfaces.bgQualityCheck.BgQualityCheck
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.versionChecker.VersionCheckerUtils
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
        @Binds fun bindsConstraints(constraintsImpl: info.nightscout.plugins.constraints.ConstraintsImpl): Constraints
    }
}