package info.nightscout.androidaps.core.dependencyInjection

import android.content.Context
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.resources.ResourceHelperImplementation
import javax.inject.Singleton

@Module
open class CoreModule {

    @Provides
    @Singleton
    fun provideResources(context: Context): ResourceHelper {
        return ResourceHelperImplementation(context)
    }
}