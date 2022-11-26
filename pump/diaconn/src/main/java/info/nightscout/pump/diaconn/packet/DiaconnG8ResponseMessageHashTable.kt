package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaconnG8ResponseMessageHashTable @Inject constructor(val injector: HasAndroidInjector) {
    var messages: HashMap<Int, DiaconnG8Packet> = HashMap()

    fun put(message: DiaconnG8Packet) {
        messages[message.msgType.toInt()] = message
    }

    fun findMessage(command: Int): DiaconnG8Packet {
        return messages[command] ?: DiaconnG8Packet(injector)
    }

    init {
        put(BigMainInfoInquireResponsePacket(injector))
        put(BigAPSMainInfoInquireResponsePacket(injector))
        put(BigLogInquireResponsePacket(injector))
        put(InjectionSnackInquireResponsePacket(injector))
        put(SneckLimitInquireResponsePacket(injector))
        put(BasalLimitInquireResponsePacket(injector))
        put(TempBasalInquireResponsePacket(injector))
        put(TimeInquireResponsePacket(injector))
        put(TimeReportPacket(injector))
        put(LogStatusInquireResponsePacket(injector))
        put(IncarnationInquireResponsePacket(injector))
        put(BolusSpeedInquireResponsePacket(injector))
        put(SoundInquireResponsePacket(injector))
        put(DisplayTimeInquireResponsePacket(injector))
        put(LanguageInquireResponsePacket(injector))
        put(SerialNumInquireResponsePacket(injector))


        // Report Packet
        put(BasalPauseReportPacket(injector))
        put(BasalSettingReportPacket(injector))
        put(ConfirmReportPacket(injector))
        put(InjectionBasalReportPacket(injector))
        put(RejectReportPacket(injector))
        put(TempBasalReportPacket(injector))
        put(InjectionSnackResultReportPacket(injector))
        put(InjectionExtendedBolusResultReportPacket(injector))
        put(InsulinLackReportPacket(injector))
        put(BatteryWarningReportPacket(injector))
        put(InjectionBlockReportPacket(injector))
        put(BolusSpeedSettingReportPacket(injector))
    }
}
