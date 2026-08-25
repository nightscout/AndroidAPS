package app.aaps.pump.medtrum.di

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
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class MedtrumCommModule {

}
