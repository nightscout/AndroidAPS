package info.nightscout.androidaps.dependencyInjection

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.ProfileStore
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.plugins.aps.logger.LoggerCallback
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.plugins.aps.openAPSAMA.DetermineBasalResultAMA
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.actions.*
import info.nightscout.androidaps.plugins.general.automation.elements.*
import info.nightscout.androidaps.plugins.general.automation.triggers.*
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction
import info.nightscout.androidaps.plugins.general.smsCommunicator.AuthRequest
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobOref1Thread
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobThread
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SendAndListen
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SetPreamble
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioResponse
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.*
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUITask
import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.queue.commands.*
import info.nightscout.androidaps.setupwizard.SWEventListener
import info.nightscout.androidaps.setupwizard.SWScreen
import info.nightscout.androidaps.setupwizard.elements.*
import info.nightscout.androidaps.utils.wizard.BolusWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        ActivitiesModule::class,
        FragmentsModule::class,
        AppModule::class,
        ReceiversModule::class,
        ServicesModule::class,
        AutomationModule::class,
        CommandQueueModule::class,
        ObjectivesModule::class,
        WizardModule::class,
        MedtronicModule::class,
        APSModule::class,
        PreferencesModule::class,
        OverviewModule::class,
        DataClassesModule::class,
        SMSModule::class
    ]
)
interface AppComponent : AndroidInjector<MainApp> {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(mainApp: MainApp): Builder

        fun build(): AppComponent
    }
}