package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

import java.nio.ByteBuffer
import kotlin.experimental.or

class AlertConfiguration(
    private val slot: AlertSlot,
    private val enabled: Boolean,
    private val durationInMinutes: Short,
    private val autoOff: Boolean,
    private val triggerType: AlertTriggerType,
    private val offsetInMinutesOrThresholdInMicroLiters: Short,
    private val beepType: BeepType,
    private val beepRepetition: BeepRepetitionType
) : Encodable {

    override val encoded: ByteArray
        get() {
            var firstByte = (slot.value.toInt() shl 4).toByte()
            if (enabled) {
                firstByte = firstByte or (1 shl 3)
            }
            if (triggerType == AlertTriggerType.RESERVOIR_VOLUME_TRIGGER) {
                firstByte = firstByte or (1 shl 2)
            }
            if (autoOff) {
                firstByte = firstByte or (1 shl 1)
            }
            firstByte = firstByte or ((durationInMinutes.toInt() shr 8 and 0x01).toByte())
            return ByteBuffer.allocate(6) //
                .put(firstByte)
                .put(durationInMinutes.toByte()) //
                .putShort(offsetInMinutesOrThresholdInMicroLiters) //
                .put(beepRepetition.value) //
                .put(beepType.value) //
                .array()
        }

    override fun toString(): String {
        return "AlertConfiguration{" +
            "slot=" + slot +
            ", enabled=" + enabled +
            ", durationInMinutes=" + durationInMinutes +
            ", autoOff=" + autoOff +
            ", triggerType=" + triggerType +
            ", offsetInMinutesOrThresholdInMicroLiters=" + offsetInMinutesOrThresholdInMicroLiters +
            ", beepType=" + beepType +
            ", beepRepetition=" + beepRepetition +
            '}'
    }
}