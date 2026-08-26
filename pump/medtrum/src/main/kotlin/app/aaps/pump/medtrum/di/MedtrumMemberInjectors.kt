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
import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.medtrum.services.MedtrumService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the Medtrum packets - the `@ContributesAndroidInjector` replacement.
 *
 * Same shape as `DiaconnMemberInjectors`: a packet is built with `new` by the service, so it fills its
 * own fields from this map afterwards. [FeatureMemberInjectors] keeps these entries out of the map each
 * graph extension declares - without it they are the same multibinding and turn up everywhere.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object MedtrumMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ActivatePacket::class)
    fun bindActivatePacket(injector: MembersInjector<ActivatePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AuthorizePacket::class)
    fun bindAuthorizePacket(injector: MembersInjector<AuthorizePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CancelBolusPacket::class)
    fun bindCancelBolusPacket(injector: MembersInjector<CancelBolusPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CancelTempBasalPacket::class)
    fun bindCancelTempBasalPacket(injector: MembersInjector<CancelTempBasalPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ClearPumpAlarmPacket::class)
    fun bindClearPumpAlarmPacket(injector: MembersInjector<ClearPumpAlarmPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(GetDeviceTypePacket::class)
    fun bindGetDeviceTypePacket(injector: MembersInjector<GetDeviceTypePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(GetRecordPacket::class)
    fun bindGetRecordPacket(injector: MembersInjector<GetRecordPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(GetTimePacket::class)
    fun bindGetTimePacket(injector: MembersInjector<GetTimePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MedtrumPacket::class)
    fun bindMedtrumPacket(injector: MembersInjector<MedtrumPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(NotificationPacket::class)
    fun bindNotificationPacket(injector: MembersInjector<NotificationPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(PollPatchPacket::class)
    fun bindPollPatchPacket(injector: MembersInjector<PollPatchPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(PrimePacket::class)
    fun bindPrimePacket(injector: MembersInjector<PrimePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ReadBolusStatePacket::class)
    fun bindReadBolusStatePacket(injector: MembersInjector<ReadBolusStatePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ResumePumpPacket::class)
    fun bindResumePumpPacket(injector: MembersInjector<ResumePumpPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SetBasalProfilePacket::class)
    fun bindSetBasalProfilePacket(injector: MembersInjector<SetBasalProfilePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SetBolusMotorPacket::class)
    fun bindSetBolusMotorPacket(injector: MembersInjector<SetBolusMotorPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SetBolusPacket::class)
    fun bindSetBolusPacket(injector: MembersInjector<SetBolusPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SetPatchPacket::class)
    fun bindSetPatchPacket(injector: MembersInjector<SetPatchPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SetTempBasalPacket::class)
    fun bindSetTempBasalPacket(injector: MembersInjector<SetTempBasalPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SetTimePacket::class)
    fun bindSetTimePacket(injector: MembersInjector<SetTimePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SetTimeZonePacket::class)
    fun bindSetTimeZonePacket(injector: MembersInjector<SetTimeZonePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(StopPatchPacket::class)
    fun bindStopPatchPacket(injector: MembersInjector<StopPatchPacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SubscribePacket::class)
    fun bindSubscribePacket(injector: MembersInjector<SubscribePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SynchronizePacket::class)
    fun bindSynchronizePacket(injector: MembersInjector<SynchronizePacket>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MedtrumService::class)
    fun bindMedtrumService(injector: MembersInjector<MedtrumService>): MembersInjector<*> = injector
}
