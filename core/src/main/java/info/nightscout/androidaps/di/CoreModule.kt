package info.nightscout.androidaps.di

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelperImplementation
import javax.inject.Singleton

@Module(
    includes = [
        CoreReceiversModule::class,
        CoreFragmentsModule::class,
        CoreDataClassesModule::class
    ]
)
open class CoreModule {

    @Provides
    @Singleton
    fun provideResources(context: Context, fabricPrivacy: FabricPrivacy): ResourceHelper = ResourceHelperImplementation(context, fabricPrivacy)

    @Suppress("DEPRECATION")
    @Provides
    fun smsManager(context: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()
}