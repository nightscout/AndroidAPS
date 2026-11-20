package app.aaps.pump.omnipod.common.bledriver.comm.packet

import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType

/**
 * BLE packet framing parameters. Dash and Omnipod 5 share the same header byte layout but
 * use different maximum payload sizes per packet (Dash: 20 bytes, O5: 244 bytes), which in
 * turn changes every derived fragment capacity used when splitting/joining message payloads.
 *
 * Ported from OmnipodKit's BlePodProfile.swift (loopandlearn/OmnipodKit) `BlePacketLayout`
 * struct and its `omnipodDash`/`omnipod5` presets.
 */
data class BlePacketLayout(
    val maxPayloadSize: Int,
    val maxFragments: Int,
    val firstPacketHeaderSizeWithoutMiddlePackets: Int,
    val firstPacketHeaderSizeWithMiddlePackets: Int,
    val lastPacketHeaderSize: Int,
    /**
     * Whether [BlePacket.toByteArray] zero-pads every packet out to [maxPayloadSize].
     *
     * OmnipodKit's BLEPacket.swift never pads - it writes exactly the bytes it built (its
     * `Data(capacity:)` is only an allocation hint, not a length). This codebase's Dash
     * path has always padded, and since Dash's [maxPayloadSize] is 20 that only ever added
     * a few trailing zero bytes to the final packet of a message; it's proven correct
     * against real Dash hardware over years of use, so it stays enabled there rather than
     * being "fixed" to match Swift.
     *
     * For O5 the same behavior is materially different: [maxPayloadSize] is 244, so a
     * 44-byte pairing message went out as a 244-byte BLE write with 193 bytes of zero
     * padding, where a real PDM sends 51. Real-hardware logs show a pod does still ACK
     * those (it reads the packet's own length field and ignores the tail), so this was not
     * what broke pairing - but it is a genuine divergence from the reference on every
     * single write, and the multi-packet path it also affects (SPS2.1, ~642 bytes) has
     * never yet run against a real pod. Disabled for O5 to match Swift byte-for-byte.
     */
    val padToMaxPayloadSize: Boolean
) {
    val firstPacketCapacityWithoutMiddlePackets: Int
        get() = maxPayloadSize - firstPacketHeaderSizeWithoutMiddlePackets

    val firstPacketCapacityWithMiddlePackets: Int
        get() = maxPayloadSize - firstPacketHeaderSizeWithMiddlePackets

    val firstPacketCapacityWithOptionalPlusOnePacket: Int
        get() = firstPacketCapacityWithMiddlePackets

    val middlePacketCapacity: Int
        get() = maxPayloadSize - 1

    val lastPacketCapacity: Int
        get() = maxPayloadSize - lastPacketHeaderSize

    companion object {

        val DASH = BlePacketLayout(
            maxPayloadSize = 20,
            maxFragments = 15,
            firstPacketHeaderSizeWithoutMiddlePackets = 7,
            firstPacketHeaderSizeWithMiddlePackets = 2,
            lastPacketHeaderSize = 6,
            padToMaxPayloadSize = true
        )

        val OMNIPOD_5 = BlePacketLayout(
            maxPayloadSize = 244,
            maxFragments = 15,
            firstPacketHeaderSizeWithoutMiddlePackets = 7,
            firstPacketHeaderSizeWithMiddlePackets = 2,
            lastPacketHeaderSize = 6,
            padToMaxPayloadSize = false
        )
    }
}

val PodType.blePacketLayout: BlePacketLayout
    get() = if (isO5) BlePacketLayout.OMNIPOD_5 else BlePacketLayout.DASH
