package info.nightscout.androidaps.dependencyInjection

import android.content.Context
import android.preference.PreferenceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.MainApp
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
    fun provideResources(mainApp: MainApp): ResourceHelper {
        return ResourceHelperImplementation(mainApp)
    }

    @Module
    interface AppBindings {

        @Binds
        fun bindContext(mainApp: MainApp): Context
    }
}
