package app.aaps.pump.medtrum.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.pump.medtrum.comm.packets.ActivatePacket
import app.aaps.pump.medtrum.comm.packets.AuthorizePacket
import app.aaps.pump.medtrum.comm.packets.CancelBolusPacket
import app.aaps.pump.medtrum.comm.packets.CancelTempBasalPacket
import app.aaps.pump.medtrum.comm.packets.ClearPumpAlarmPacket
import app.aaps.pump.medtrum.comm.packets.GetDeviceTypePacket
import app.aaps.pump.medtrum.comm.packets.GetRecordPacket
import app.aaps.pump.medtrum.comm.packets.GetTimePacket
import app.aaps.pump.medtrum.comm.packets.MedtrumPacket
import app.aaps.pump.medtrum.comm.packets.NotificationPacket
import app.aaps.pump.medtrum.comm.packets.PollPatchPacket
import app.aaps.pump.medtrum.comm.packets.PrimePacket
import app.aaps.pump.medtrum.comm.packets.ReadBolusStatePacket
import app.aaps.pump.medtrum.comm.packets.ResumePumpPacket
import app.aaps.pump.medtrum.comm.packets.SetBasalProfilePacket
import app.aaps.pump.medtrum.comm.packets.SetBolusMotorPacket
import app.aaps.pump.medtrum.comm.packets.SetBolusPacket
import app.aaps.pump.medtrum.comm.packets.SetPatchPacket
import app.aaps.pump.medtrum.comm.packets.SetTempBasalPacket
import app.aaps.pump.medtrum.comm.packets.SetTimePacket
import app.aaps.pump.medtrum.comm.packets.SetTimeZonePacket
import app.aaps.pump.medtrum.comm.packets.StopPatchPacket
import app.aaps.pump.medtrum.comm.packets.SubscribePacket
import app.aaps.pump.medtrum.comm.packets.SynchronizePacket

@Suppress("unused")
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
