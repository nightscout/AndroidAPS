package info.nightscout.androidaps.dependencyInjection

import android.content.Context
import android.preference.PreferenceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctionImplementation
import info.nightscout.androidaps.plugins.general.automation.actions.ActionSendSMS
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
    fun provideResources(mainApp: MainApp): ResourceHelper {
        return ResourceHelperImplementation(mainApp)
    }

    @Module
    interface AppBindings {

        @ContributesAndroidInjector
        fun bindDataService(): DataService

        @ContributesAndroidInjector
        fun bindCommandSetProfile(): CommandSetProfile

        @ContributesAndroidInjector
        fun bindActionSendSMS(): ActionSendSMS

        @Binds
        fun bindContext(mainApp: MainApp): Context
    }
}
