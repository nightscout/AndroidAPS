package info.nightscout.androidaps.dependencyInjection

import android.content.Context
import android.preference.PreferenceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.AAPSLoggerDebug
import info.nightscout.androidaps.logging.AAPSLoggerProduction
import info.nightscout.androidaps.plugins.aps.openAPSMA.LoggerCallback
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctionImplementation
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.androidaps.plugins.general.automation.actions.ActionSendSMS
import info.nightscout.androidaps.queue.commands.CommandSetProfile
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.resources.ResourceHelperImplementation
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.sharedPreferences.SPImplementation
import javax.inject.Singleton

@Module(includes = [AppModule.AppBindings::class])
open class AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context, resourceHelper: ResourceHelper): SP {
        return SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), resourceHelper)
    }

    @Provides
    @Singleton
    fun provideProfileFunction(sp: SP, configBuilderPlugin: ConfigBuilderPlugin): ProfileFunction {
        return ProfileFunctionImplementation(sp, configBuilderPlugin)
    }

    @Provides
    @Singleton
    fun provideResources(mainApp: MainApp): ResourceHelper {
        return ResourceHelperImplementation(mainApp)
    }

    @Provides
    @Singleton
    fun provideAAPSLogger(): AAPSLogger {
        return if (BuildConfig.DEBUG) {
            AAPSLoggerDebug()
        } else {
            AAPSLoggerProduction()
        }
    }

    @Module
    interface AppBindings {

        @ContributesAndroidInjector fun commandSetProfileInjector(): CommandSetProfile
        @ContributesAndroidInjector fun actionSendSMSInjector(): ActionSendSMS
        @ContributesAndroidInjector fun objective0Injector(): Objective0
        @ContributesAndroidInjector fun objective1Injector(): Objective1
        @ContributesAndroidInjector fun objective2Injector(): Objective2
        @ContributesAndroidInjector fun objective3Injector(): Objective3
        @ContributesAndroidInjector fun objective5Injector(): Objective5
        @ContributesAndroidInjector fun objective6Injector(): Objective6
        @ContributesAndroidInjector fun loggerCallbackInjector(): LoggerCallback

        @Binds fun bindContext(mainApp: MainApp): Context
    }
}
