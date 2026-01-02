package app.aaps.pump.omnipod.dash.driver.pod.definition

import java.nio.ByteBuffer
import kotlin.experimental.or

class AlertConfiguration(
    private val type: AlertType,
    private val enabled: Boolean,
    private val durationInMinutes: Short,
    private val autoOff: Boolean,
    private val trigger: AlertTrigger,
    private val beepType: BeepType,
    private val beepRepetition: BeepRepetitionType
) : Encodable {

    override val encoded: ByteArray
        get() {
            var firstByte = (type.index.toInt() shl 4).toByte()
            if (enabled) {
                firstByte = (firstByte.toInt() or (1 shl 3)).toByte()
            }
            if (trigger is AlertTrigger.ReservoirVolumeTrigger) {
                firstByte = (firstByte.toInt() or (1 shl 2)).toByte()
            }
            if (autoOff) {
                firstByte = (firstByte.toInt() or (1 shl 1)).toByte()
            }
            firstByte = firstByte or ((durationInMinutes.toInt() shr 8 and 0x01).toByte())
            return ByteBuffer.allocate(6)
                .put(firstByte)
                .put(durationInMinutes.toByte())
                .putShort(
                    when (trigger) {
                        is AlertTrigger.ReservoirVolumeTrigger -> {
                            trigger.thresholdInMicroLiters
                        }

                        is AlertTrigger.TimerTrigger           -> {
                            trigger.offsetInMinutes
                        }
                    }
                )
                .put(beepRepetition.value)
                .put(beepType.value)
                .array()
        }

    override fun toString(): String {
        return "AlertConfiguration{" +
            "type=" + type +
            ", enabled=" + enabled +
            ", durationInMinutes=" + durationInMinutes +
            ", autoOff=" + autoOff +
            ", trigger=" + trigger +
            ", beepType=" + beepType +
            ", beepRepetition=" + beepRepetition +
            '}'
    }
}
