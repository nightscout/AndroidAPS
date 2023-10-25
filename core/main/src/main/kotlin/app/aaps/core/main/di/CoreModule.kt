package app.aaps.core.main.di

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import app.aaps.core.main.wizard.BolusWizard
import app.aaps.core.main.wizard.QuickWizardEntry
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector

@Module(
    includes = [
        CoreModule.CoreDataClassesModule::class,
    ]
)
open class CoreModule {
    @Module
    abstract class CoreDataClassesModule {

        @ContributesAndroidInjector abstract fun bolusWizardInjector(): BolusWizard
        @ContributesAndroidInjector abstract fun quickWizardEntryInjector(): QuickWizardEntry
    }


    @Suppress("DEPRECATION")
    @Provides
    fun smsManager(context: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()
}