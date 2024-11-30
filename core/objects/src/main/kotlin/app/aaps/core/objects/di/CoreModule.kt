package app.aaps.core.objects.di

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import app.aaps.core.objects.aps.DetermineBasalResult
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector

@Module(
    includes = [
        CoreModule.Bindings::class,
    ]
)
open class CoreModule {

    @Suppress("unused")
    @Module
    interface Bindings {

        @ContributesAndroidInjector fun determineBasalResultInjector(): DetermineBasalResult
        @ContributesAndroidInjector fun bolusWizardInjector(): BolusWizard
        @ContributesAndroidInjector fun quickWizardEntryInjector(): QuickWizardEntry
    }

    @Suppress("DEPRECATION")
    @Provides
    fun smsManager(context: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()
}