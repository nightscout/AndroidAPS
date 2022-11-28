package info.nightscout.plugins.support.di

import dagger.Binds
import dagger.Module
import info.nightscout.interfaces.bgQualityCheck.BgQualityCheck
import info.nightscout.interfaces.versionChecker.VersionCheckerUtils
import info.nightscout.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import info.nightscout.plugins.constraints.versionChecker.VersionCheckerUtilsImpl

@Module(
    includes = [
        PluginsSupportModule.Bindings::class
    ]
)

@Suppress("unused")
abstract class PluginsSupportModule {

    @Module
    interface Bindings {

        @Binds fun bindProcessedDeviceStatusData(versionCheckerUtils: VersionCheckerUtilsImpl): VersionCheckerUtils
        @Binds fun bindBgQualityCheck(bgQualityCheck: BgQualityCheckPlugin): BgQualityCheck
    }
}