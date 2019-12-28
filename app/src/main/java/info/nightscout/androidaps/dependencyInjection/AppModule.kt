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
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctionImplementation
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective5
import info.nightscout.androidaps.plugins.general.automation.actions.ActionSendSMS
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.commands.CommandSetProfile
import info.nightscout.androidaps.services.DataService
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.resources.ResourceHelperImplementation
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.sharedPreferences.SPImplementation
import javax.inject.Singleton

@Module(includes = [AppModule.AppBindings::class])
class AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SP {
        return SPImplementation(PreferenceManager.getDefaultSharedPreferences(context))
    }

    @Provides
    @Singleton
    fun provideProfileFunction(sp: SP): ProfileFunction {
        return ProfileFunctionImplementation(sp)
    }

    @Provides
    @Singleton
    fun provideConstraintChecker(mainApp: MainApp): ConstraintChecker {
        return ConstraintChecker(mainApp)
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

        @ContributesAndroidInjector
        fun dataServiceInjector(): DataService

        @ContributesAndroidInjector
        fun commandSetProfileInjector(): CommandSetProfile

        @ContributesAndroidInjector
        fun actionSendSMSInjector(): ActionSendSMS

        @ContributesAndroidInjector
        fun objective5Injector(): Objective5

        @ContributesAndroidInjector
        fun loggerCallbackInjector(): LoggerCallback

        @Binds
        fun bindContext(mainApp: MainApp): Context
    }
}
