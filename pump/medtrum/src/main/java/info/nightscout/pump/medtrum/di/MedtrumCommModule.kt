package info.nightscout.pump.medtrum.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.medtrum.comm.packets.ActivatePacket
import info.nightscout.pump.medtrum.comm.packets.AuthorizePacket
import info.nightscout.pump.medtrum.comm.packets.CancelBolusPacket
import info.nightscout.pump.medtrum.comm.packets.CancelTempBasalPacket
import info.nightscout.pump.medtrum.comm.packets.ClearPumpAlarmPacket
import info.nightscout.pump.medtrum.comm.packets.GetDeviceTypePacket
import info.nightscout.pump.medtrum.comm.packets.GetRecordPacket
import info.nightscout.pump.medtrum.comm.packets.GetTimePacket
import info.nightscout.pump.medtrum.comm.packets.MedtrumPacket
import info.nightscout.pump.medtrum.comm.packets.NotificationPacket
import info.nightscout.pump.medtrum.comm.packets.PollPatchPacket
import info.nightscout.pump.medtrum.comm.packets.PrimePacket
import info.nightscout.pump.medtrum.comm.packets.ReadBolusStatePacket
import info.nightscout.pump.medtrum.comm.packets.ResumePumpPacket
import info.nightscout.pump.medtrum.comm.packets.SetBasalProfilePacket
import info.nightscout.pump.medtrum.comm.packets.SetBolusMotorPacket
import info.nightscout.pump.medtrum.comm.packets.SetBolusPacket
import info.nightscout.pump.medtrum.comm.packets.SetPatchPacket
import info.nightscout.pump.medtrum.comm.packets.SetTempBasalPacket
import info.nightscout.pump.medtrum.comm.packets.SetTimePacket
import info.nightscout.pump.medtrum.comm.packets.SetTimeZonePacket
import info.nightscout.pump.medtrum.comm.packets.StopPatchPacket
import info.nightscout.pump.medtrum.comm.packets.SubscribePacket
import info.nightscout.pump.medtrum.comm.packets.SynchronizePacket

@Module
abstract class MedtrumCommModule {

    @ContributesAndroidInjector abstract fun contributesActivatePacket(): ActivatePacket
    @ContributesAndroidInjector abstract fun contributesAuthorizePacket(): AuthorizePacket
    @ContributesAndroidInjector abstract fun contributesCancelBolusPacket(): CancelBolusPacket
    @ContributesAndroidInjector abstract fun contributesCancelTempBasalPacket(): CancelTempBasalPacket
    @ContributesAndroidInjector abstract fun contributesClearPumpAlarmPacket(): ClearPumpAlarmPacket
    @ContributesAndroidInjector abstract fun contributesGetDeviceTypePacket(): GetDeviceTypePacket
    @ContributesAndroidInjector abstract fun contributesGetRecordPacket(): GetRecordPacket
    @ContributesAndroidInjector abstract fun contributesGetTimePacket(): GetTimePacket
    @ContributesAndroidInjector abstract fun contributesMedtrumPacket(): MedtrumPacket
    @ContributesAndroidInjector abstract fun contributesNotificationPacket(): NotificationPacket
    @ContributesAndroidInjector abstract fun contributesPollPatchPacket(): PollPatchPacket
    @ContributesAndroidInjector abstract fun contributesPrimePacket(): PrimePacket
    @ContributesAndroidInjector abstract fun contributesReadBolusStatePacket(): ReadBolusStatePacket
    @ContributesAndroidInjector abstract fun contributesResumePumpPacket(): ResumePumpPacket
    @ContributesAndroidInjector abstract fun contributesSetBasalProfilePacket(): SetBasalProfilePacket
    @ContributesAndroidInjector abstract fun contributesSetBolusMotorPacket(): SetBolusMotorPacket
    @ContributesAndroidInjector abstract fun contributesSetBolusPacket(): SetBolusPacket
    @ContributesAndroidInjector abstract fun contributesSetPatchPacket(): SetPatchPacket
    @ContributesAndroidInjector abstract fun contributesSetTempBasalPacket(): SetTempBasalPacket
    @ContributesAndroidInjector abstract fun contributesSetTimePacket(): SetTimePacket
    @ContributesAndroidInjector abstract fun contributesSetTimeZonePacket(): SetTimeZonePacket
    @ContributesAndroidInjector abstract fun contributesStopPatchPacket(): StopPatchPacket
    @ContributesAndroidInjector abstract fun contributesSubscribePacket(): SubscribePacket
    @ContributesAndroidInjector abstract fun contributesSynchronizePacket(): SynchronizePacket
}
