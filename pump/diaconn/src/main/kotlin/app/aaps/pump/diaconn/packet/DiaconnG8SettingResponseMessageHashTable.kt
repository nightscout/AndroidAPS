package app.aaps.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaconnG8SettingResponseMessageHashTable @Inject constructor(
    val injector: HasAndroidInjector
) {

    var messages: HashMap<Int, DiaconnG8Packet> = HashMap()

    fun put(message: DiaconnG8Packet) {
        messages[message.msgType.toInt()] = message
    }

    fun findMessage(command: Int): DiaconnG8Packet {
        return messages[command] ?: DiaconnG8Packet(injector)
    }

    init {
        put(AppCancelSettingResponsePacket(injector))
        put(AppConfirmSettingResponsePacket(injector))
        put(BasalPauseSettingResponsePacket(injector))
        put(BasalSettingResponsePacket(injector))
        put(TempBasalSettingResponsePacket(injector))
        put(TimeSettingResponsePacket(injector))
        put(InjectionBasalSettingResponsePacket(injector))
        put(InjectionSnackSettingResponsePacket(injector))
        put(InjectionExtendedBolusSettingResponsePacket(injector))
        put(InjectionCancelSettingResponsePacket(injector))
        put(SoundSettingResponsePacket(injector))
        put(DisplayTimeoutSettingResponsePacket(injector))
        put(LanguageSettingResponsePacket(injector))
        put(BolusSpeedSettingResponsePacket(injector))
    }
}
