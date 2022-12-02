package info.nightscout.core.di

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import dagger.Module
import dagger.Provides

@Module(
    includes = [
        CoreDataClassesModule::class,
        PreferencesModule::class
    ]
)
open class CoreModule {

    @Suppress("DEPRECATION")
    @Provides
    fun smsManager(context: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()
}