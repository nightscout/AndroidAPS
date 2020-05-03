package info.nightscout.androidaps.core.dependencyInjection

import android.content.Context
import android.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.resources.ResourceHelperImplementation
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.sharedPreferences.SPImplementation
import javax.inject.Singleton

@Module
open class CoreModule {

    @Provides
    @Singleton
    fun provideResources(context: Context): ResourceHelper {
        return ResourceHelperImplementation(context)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context, resourceHelper: ResourceHelper): SP {
        return SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), resourceHelper)
    }
}