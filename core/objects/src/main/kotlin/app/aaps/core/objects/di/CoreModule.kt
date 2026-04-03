package app.aaps.core.objects.di

import android.content.Context
import android.telephony.SmsManager
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Suppress("unused")
@Module(
    includes = [
        CoreModule.Bindings::class,
    ]
)
@InstallIn(SingletonComponent::class)
open class CoreModule {

    @Suppress("unused")
    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @ContributesAndroidInjector fun bolusWizardInjector(): BolusWizard
        @ContributesAndroidInjector fun quickWizardEntryInjector(): QuickWizardEntry
    }

    @Suppress("DEPRECATION")
    @Provides
    fun smsManager(context: Context): SmsManager? = context.getSystemService(SmsManager::class.java)
}