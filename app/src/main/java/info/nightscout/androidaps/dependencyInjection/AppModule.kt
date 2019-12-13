package info.nightscout.androidaps.dependencyInjection

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Singleton


@Module
class AppModule(private val application: MainApp) {
    @Provides
    @Singleton
    fun provideApplication(): MainApp {
        return application
    }

    @Provides
    fun provideContext(): Context {
        return application.applicationContext
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(): SP {
        return SP(PreferenceManager.getDefaultSharedPreferences(provideContext()))
    }
}
