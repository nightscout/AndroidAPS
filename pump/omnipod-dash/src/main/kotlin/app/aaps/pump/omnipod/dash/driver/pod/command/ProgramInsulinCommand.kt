package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.command.base.CommandType
import app.aaps.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.ShortInsulinProgramElement
import java.nio.ByteBuffer
import java.util.*

// Always followed by one of: 0x13, 0x16, 0x17
class ProgramInsulinCommand internal constructor(
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    nonce: Int,
    insulinProgramElements:
    List<ShortInsulinProgramElement>,
    private val checksum: Short,
    private val byte9: Byte,
    private val byte10And11: Short,
    private val byte12And13: Short,
    private val deliveryType: DeliveryType
) : NonceEnabledCommand(CommandType.PROGRAM_INSULIN, uniqueId, sequenceNumber, multiCommandFlag, nonce) {

    private val insulinProgramElements: List<ShortInsulinProgramElement> = ArrayList(insulinProgramElements)

    fun getLength(): Short = (insulinProgramElements.size * 2 + 14).toShort()

    fun getBodyLength(): Byte = (insulinProgramElements.size * 2 + 12).toByte()

    enum class DeliveryType(private val value: Byte) {
        BASAL(0x00.toByte()), TEMP_BASAL(0x01.toByte()), BOLUS(0x02.toByte());

        fun getValue(): Byte {
            return value
        }
    }

    override val encoded: ByteArray
        get() {
            val buffer = ByteBuffer.allocate(getLength().toInt())
                .put(commandType.value)
                .put(getBodyLength())
                .putInt(nonce)
                .put(deliveryType.getValue())
                .putShort(checksum)
                .put(byte9) // BASAL: currentSlot // BOLUS: number of ShortInsulinProgramElements
                .putShort(byte10And11) // BASAL: remainingEighthSecondsInCurrentSlot // BOLUS: immediate pulses multiplied by delay between pulses in eighth seconds
                .putShort(byte12And13) // BASAL: remainingPulsesInCurrentSlot // BOLUS: immediate pulses
            for (element in insulinProgramElements) {
                buffer.put(element.encoded)
            }
            return buffer.array()
        }

    override fun toString(): String {
        return "ProgramInsulinCommand{" +
            "insulinProgramElements=" + insulinProgramElements +
            ", checksum=" + checksum +
            ", byte9=" + byte9 +
            ", byte10And11=" + byte10And11 +
            ", byte12And13=" + byte12And13 +
            ", deliveryType=" + deliveryType +
            ", nonce=" + nonce +
            ", commandType=" + commandType +
            ", uniqueId=" + uniqueId +
            ", sequenceNumber=" + sequenceNumber +
            ", multiCommandFlag=" + multiCommandFlag +
            '}'
    }
}
