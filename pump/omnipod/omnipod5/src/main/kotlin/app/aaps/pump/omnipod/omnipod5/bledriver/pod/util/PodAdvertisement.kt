package app.aaps.pump.omnipod.omnipod5.bledriver.pod.util

import android.os.ParcelUuid
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType

/**
 * Parses a BLE advertisement's service UUID list into pod identity fields, branching on
 * [PodType] since Dash and Omnipod 5 pods advertise completely different UUID layouts.
 *
 * Ported from OmnipodKit's PodAdvertisement.swift (loopandlearn/OmnipodKit). This is an
 * additive, standalone parser - it does not replace the existing Dash-only parsing in
 * [app.aaps.pump.omnipod.common.bledriver.comm.legacy.scan.BleDiscoveredDevice]. Wiring pod-type
 * detection into the live scan path is a separate, follow-up change so existing Dash pairing
 * is not put at risk by this addition.
 *
 * Unlike the Swift original (a failable initializer that throws/returns nil on malformed
 * input), [parse] simply returns null for anything that doesn't match the expected shape
 * for the given [PodType].
 */
data class PodAdvertisement private constructor(
    val podType: PodType,
    /** Dash only: the pod's 32-bit id, decoded from service UUIDs 3 & 4. */
    val podId: Long?,
    /** Dash only: the pod's lot number, decoded from service UUIDs 5-7. */
    val lotNo: Long?,
    /** Dash only: the pod's sequence number, decoded from service UUIDs 7-8. */
    val sequenceNo: Int?,
    /** Omnipod 5 only: the PDM id embedded in the single 128-bit service UUID. */
    val pdmId: Long?,
    /**
     * True if this advertisement represents a pod that has not yet been paired to any PDM
     * (i.e. still showing its placeholder id), and is therefore eligible for pairing.
     */
    val pairable: Boolean
) {

    companion object {

        private const val DASH_MAIN_SERVICE_UUID = "4024"
        private const val DASH_UNKNOWN_THIRD_SERVICE_UUID = "000a"
        private const val DASH_UNPAIRED_POD_ID_HIGH = "ffff"
        private const val DASH_UNPAIRED_POD_ID_LOW = "fffe"
        private const val O5_UNPAIRED_PDM_ID_HEX = "fffffffe"

        /**
         * Attempts to parse [serviceUuids] as an advertisement from a pod of the given
         * [podType]. Returns null if the advertisement doesn't match the shape expected
         * for that pod type (wrong UUID count, wrong fixed UUIDs, unparseable ids, etc).
         */
        fun parse(serviceUuids: List<ParcelUuid>?, podType: PodType): PodAdvertisement? {
            if (serviceUuids.isNullOrEmpty()) return null
            return when (podType) {
                PodType.DASH      -> parseDash(serviceUuids)
                PodType.OMNIPOD_5 -> parseO5(serviceUuids)
                PodType.EROS,
                PodType.UNKNOWN   -> null
            }
        }

        /** Extracts the 16-bit UUID hex (4 chars) from a standard 36-char UUID string. */
        private fun extractUuid16(uuid: ParcelUuid): String =
            uuid.toString().substring(4, 8)

        private fun parseDash(serviceUuids: List<ParcelUuid>): PodAdvertisement? {
            if (serviceUuids.size != 9) return null
            if (extractUuid16(serviceUuids[0]) != DASH_MAIN_SERVICE_UUID) return null
            if (extractUuid16(serviceUuids[2]) != DASH_UNKNOWN_THIRD_SERVICE_UUID) return null

            val podIdHigh = extractUuid16(serviceUuids[3])
            val podIdLow = extractUuid16(serviceUuids[4])
            val podId = (podIdHigh + podIdLow).toLongOrNull(16) ?: return null

            val lotSeqPrefix = extractUuid16(serviceUuids[5]) +
                extractUuid16(serviceUuids[6]) +
                extractUuid16(serviceUuids[7])
            if (lotSeqPrefix.length < 10) return null
            val lotNo = lotSeqPrefix.substring(0, 10).toLongOrNull(16) ?: return null

            val seqHex = extractUuid16(serviceUuids[7]) + extractUuid16(serviceUuids[8])
            if (seqHex.length < 3) return null
            val sequenceNo = seqHex.substring(2).toIntOrNull(16) ?: return null

            val pairable = podIdHigh.equals(DASH_UNPAIRED_POD_ID_HIGH, ignoreCase = true) &&
                podIdLow.equals(DASH_UNPAIRED_POD_ID_LOW, ignoreCase = true)

            return PodAdvertisement(
                podType = PodType.DASH,
                podId = podId,
                lotNo = lotNo,
                sequenceNo = sequenceNo,
                pdmId = null,
                pairable = pairable
            )
        }

        private fun parseO5(serviceUuids: List<ParcelUuid>): PodAdvertisement? {
            if (serviceUuids.size != 1) return null

            val idString = serviceUuids[0].toString()
            if (idString.length != 36) return null

            val pdmIdHex = idString.substring(26, 34)
            val pdmId = pdmIdHex.toLongOrNull(16) ?: return null
            val pairable = pdmIdHex.equals(O5_UNPAIRED_PDM_ID_HEX, ignoreCase = true)

            return PodAdvertisement(
                podType = PodType.OMNIPOD_5,
                podId = null,
                lotNo = null,
                sequenceNo = null,
                pdmId = pdmId,
                pairable = pairable
            )
        }
    }
}
