package info.nightscout.androidaps.dependencyInjection

import android.app.Application
import android.content.Context
import android.preference.PreferenceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Singleton

@Module(includes = [AppModule.AppBindings::class])
class AppModule() {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SP {
        return SP(PreferenceManager.getDefaultSharedPreferences(context))
    }

    @Module
    interface AppBindings {

        @Binds
        fun bindContext(application: Application): Context
    }
}
