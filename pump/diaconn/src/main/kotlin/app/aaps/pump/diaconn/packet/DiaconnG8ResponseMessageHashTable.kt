package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class DiaconnG8ResponseMessageHashTable(
    private val injector: MetroMemberInjector
) {

    var messages: HashMap<Int, DiaconnG8Packet> = HashMap()

    /** Two packets with one type would leave only the last one in the table, and the other would never be read. */
    fun put(message: DiaconnG8Packet) {
        val earlier = messages.put(message.msgType.toInt(), message)
        check(earlier == null) { "${earlier?.friendlyName} and ${message.friendlyName} have the same type ${message.msgType}" }
    }

    fun findMessage(command: Int): DiaconnG8Packet {
        return messages[command] ?: DiaconnG8Packet(injector)
    }

    init {
        put(BigMainInfoInquireResponsePacket(injector))
        put(BigAPSMainInfoInquireResponsePacket(injector))
        put(BigLogInquireResponsePacket(injector))
        put(InjectionSnackInquireResponsePacket(injector))
        put(SnackLimitInquireResponsePacket(injector))
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
        put(InjectionProgressReportPacket(injector))
    }
}
