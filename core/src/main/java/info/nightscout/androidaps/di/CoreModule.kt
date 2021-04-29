package info.nightscout.androidaps.di

import android.content.Context
import android.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.AAPSLoggerProduction
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctionImplementation
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.resources.ResourceHelperImplementation
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.sharedPreferences.SPImplementation
import javax.inject.Singleton

@Module(includes = [
    CoreReceiversModule::class,
    CoreFragmentsModule::class,
    CoreDataClassesModule::class
])
open class CoreModule {

    @Provides
    @Singleton
    fun provideProfileFunction(aapsLogger: AAPSLogger, sp: SP, resourceHelper: ResourceHelper, activePlugin: ActivePlugin, repository: AppRepository, dateUtil: DateUtil): ProfileFunction {
        return ProfileFunctionImplementation(aapsLogger, sp, resourceHelper, activePlugin, repository, dateUtil)
    }

    @Provides
    @Singleton
    fun provideResources(context: Context): ResourceHelper = ResourceHelperImplementation(context)

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context, resourceHelper: ResourceHelper): SP = SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), resourceHelper)

    @Provides
    @Singleton
    fun provideAAPSLogger(l: L): AAPSLogger = AAPSLoggerProduction(l)

}