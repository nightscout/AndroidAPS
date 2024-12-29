package app.aaps.pump.medtronic.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.pump.medtronic.MedtronicFragment
import app.aaps.pump.medtronic.comm.MedtronicCommunicationManager
import app.aaps.pump.medtronic.comm.ui.MedtronicUIComm
import app.aaps.pump.medtronic.comm.ui.MedtronicUITask
import app.aaps.pump.medtronic.dialog.MedtronicHistoryActivity
import app.aaps.pump.medtronic.service.RileyLinkMedtronicService

@Module
@Suppress("unused")
abstract class MedtronicModule {

    @ContributesAndroidInjector
    abstract fun contributesMedtronicHistoryActivity(): MedtronicHistoryActivity
    @ContributesAndroidInjector abstract fun contributesMedtronicFragment(): MedtronicFragment

    @ContributesAndroidInjector
    abstract fun contributesRileyLinkMedtronicService(): RileyLinkMedtronicService

    @ContributesAndroidInjector
    abstract fun medtronicCommunicationManagerProvider(): MedtronicCommunicationManager
    @ContributesAndroidInjector abstract fun medtronicUITaskProvider(): MedtronicUITask
    @ContributesAndroidInjector abstract fun medtronicUICommProvider(): MedtronicUIComm
}