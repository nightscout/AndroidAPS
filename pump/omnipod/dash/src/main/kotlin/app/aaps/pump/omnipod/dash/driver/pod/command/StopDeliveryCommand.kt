package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.command.base.CommandType
import app.aaps.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder
import app.aaps.pump.omnipod.dash.driver.pod.definition.BeepType
import app.aaps.pump.omnipod.dash.driver.pod.definition.Encodable
import java.nio.ByteBuffer
import java.util.*

class StopDeliveryCommand private constructor(
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    private val deliveryType: DeliveryType,
    private val beepType: BeepType,
    nonce: Int
) : NonceEnabledCommand(CommandType.STOP_DELIVERY, uniqueId, sequenceNumber, multiCommandFlag, nonce) {

    override val encoded: ByteArray
        get() {
            return appendCrc(
                ByteBuffer.allocate(LENGTH + HEADER_LENGTH)
                    .put(encodeHeader(uniqueId, sequenceNumber, LENGTH, multiCommandFlag))
                    .put(commandType.value)
                    .put(BODY_LENGTH)
                    .putInt(nonce)
                    .put((beepType.value.toInt() shl 4 or deliveryType.encoded[0].toInt()).toByte())
                    .array()
            )
        }

    override fun toString(): String {
        return "StopDeliveryCommand{" +
            "deliveryType=" + deliveryType +
            ", beepType=" + beepType +
            ", nonce=" + nonce +
            ", commandType=" + commandType +
            ", uniqueId=" + uniqueId +
            ", sequenceNumber=" + sequenceNumber +
            ", multiCommandFlag=" + multiCommandFlag +
            '}'
    }

    enum class DeliveryType(
        private val basal: Boolean,
        private val tempBasal: Boolean,
        private val bolus: Boolean
    ) : Encodable {

        BASAL(true, false, false), TEMP_BASAL(false, true, false), BOLUS(false, false, true), ALL(true, true, true);

        override val encoded: ByteArray
            get() {
                val bitSet = BitSet(8)
                bitSet[0] = basal
                bitSet[1] = tempBasal
                bitSet[2] = bolus
                return bitSet.toByteArray()
            }
    }

    class Builder : NonceEnabledCommandBuilder<Builder, StopDeliveryCommand>() {

        private var deliveryType: DeliveryType? = null
        private var beepType: BeepType? = BeepType.LONG_SINGLE_BEEP

        fun setDeliveryType(deliveryType: DeliveryType): Builder {
            this.deliveryType = deliveryType
            return this
        }

        fun setBeepType(beepType: BeepType): Builder {
            this.beepType = beepType
            return this
        }

        override fun buildCommand(): StopDeliveryCommand {
            requireNotNull(deliveryType) { "deliveryType can not be null" }
            requireNotNull(beepType) { "beepType can not be null" }

            return StopDeliveryCommand(
                uniqueId!!,
                sequenceNumber!!,
                multiCommandFlag,
                deliveryType!!,
                beepType!!,
                nonce!!
            )
        }
    }

    companion object {

        private const val LENGTH: Short = 7
        private const val BODY_LENGTH: Byte = 5
    }
}
