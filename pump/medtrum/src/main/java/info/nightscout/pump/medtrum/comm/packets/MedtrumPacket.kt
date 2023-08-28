package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.extension.toInt
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

open class MedtrumPacket(protected var injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger

    var opCode: Byte = 0
    var failed = false
    var expectedMinRespLength = RESP_RESULT_END

    companion object {

        const val RESP_OPCODE_START = 1
        const val RESP_OPCODE_END = RESP_OPCODE_START + 1
        const val RESP_RESULT_START = 4
        const val RESP_RESULT_END = RESP_RESULT_START + 2

        private const val RESP_WAITING = 16384
    }

    init {
        @Suppress("LeakingThis")
        injector.androidInjector().inject(this)
    }

    open fun getRequest(): ByteArray {
        return byteArrayOf(opCode)
    }

    /**  handles a response from the Medtrum pump, returns true if command was successful, returns false if command failed or waiting for response */
    open fun handleResponse(data: ByteArray): Boolean {
        // Check for broken packets
        if (RESP_RESULT_END > data.size) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "handleResponse: Unexpected response length, expected: $expectedMinRespLength got: ${data.size}")
            return false
        }

        val incomingOpCode: Byte = data.copyOfRange(RESP_OPCODE_START, RESP_OPCODE_END).first()
        val responseCode = data.copyOfRange(RESP_RESULT_START, RESP_RESULT_END).toInt()

        return when {
            incomingOpCode != opCode     -> {
                failed = true
                aapsLogger.error(LTag.PUMPCOMM, "handleResponse: Unexpected command, expected: $opCode got: $incomingOpCode")
                false
            }

            responseCode == 0            -> {
                // Check if length is what is expected from this type of packet
                if (expectedMinRespLength > data.size) {
                    failed = true
                    aapsLogger.debug(LTag.PUMPCOMM, "handleResponse: Unexpected response length, expected: $expectedMinRespLength got: ${data.size}")
                    return false
                }
                aapsLogger.debug(LTag.PUMPCOMM, "handleResponse: Happy command: $opCode response: $responseCode")
                true
            }

            responseCode == RESP_WAITING -> {
                aapsLogger.debug(LTag.PUMPCOMM, "handleResponse: Waiting command: $opCode response: $responseCode")
                // Waiting do nothing
                false
            }

            else                         -> {
                failed = true
                aapsLogger.warn(LTag.PUMPCOMM, "handleResponse: Error in command: $opCode response: $responseCode")
                false
            }
        }
    }
}
