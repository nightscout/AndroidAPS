package app.aaps.pump.omnipod.common.bledriver.pod.util

import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType

/**
 * Centralized BLE service/characteristic UUIDs for Dash and Omnipod 5 pods, ported from
 * OmnipodKit's BluetoothServices.swift (loopandlearn/OmnipodKit).
 *
 * Dash and O5 share the same GATT *service* UUID and *command* characteristic UUID -
 * only the *data* characteristic and the pre-pairing advertisement UUID scheme differ.
 * This is additive, standalone infrastructure: existing Dash code paths
 * ([app.aaps.pump.omnipod.common.bledriver.comm.legacy.scan.BleDiscoveredDevice],
 * [app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner]) still use
 * their own inline UUID string literals and are untouched by this file; it exists so new
 * pod-type-aware scanning code has one correct place to read these values from instead of
 * re-deriving them.
 */
object BluetoothServiceUuids {

    /** GATT service UUID, identical for Dash and O5. */
    const val SERVICE_UUID = "1A7E4024-E3ED-4464-8B7E-751E03D0DC5F"

    /** Command ("CMD") characteristic UUID, identical for Dash and O5. */
    const val COMMAND_CHARACTERISTIC_UUID = "1A7E2441-E3ED-4464-8B7E-751E03D0DC5F"

    /** Dash's data characteristic UUID. */
    const val DASH_DATA_CHARACTERISTIC_UUID = "1A7E2442-E3ED-4464-8B7E-751E03D0DC5F"

    /** O5's data characteristic UUID (differs from Dash's only in this one segment). */
    const val O5_DATA_CHARACTERISTIC_UUID = "1A7E2443-E3ED-4464-8B7E-751E03D0DC5F"

    /** Dash's 16-bit advertisement service UUID, used as a BLE scan filter. */
    const val DASH_ADVERTISEMENT_UUID = "00004024-0000-1000-8000-00805f9b34fb"

    /**
     * O5's 128-bit advertisement service UUID *before pairing* (embeds a placeholder
     * PDM id of 0xFFFFFFFE), used as a BLE scan filter. After pairing, a real pod's
     * advertisement embeds the actual PDM id in the same position instead - see
     * [o5AdvertisementUuidForPdmId].
     */
    const val O5_UNPAIRED_ADVERTISEMENT_UUID = "CE1F923D-C539-48EA-7300-0AFFFFFFFE00"

    /**
     * O5 Heartbeat service, used for pod keep-alive. Distinct from the main pod
     * service/characteristics above.
     */
    const val O5_HEARTBEAT_SERVICE_UUID = "7DED7A6C-CA72-46A7-A3A2-6061F6FDCAEB"
    const val O5_HEARTBEAT_ADVERTISEMENT_UUID = "ECF301E2-674B-4474-94D0-364F3AA653E6"
    const val O5_HEARTBEAT_CHARACTERISTIC_UUID = "7DED7A6D-CA72-46A7-A3A2-6061F6FDCAEB"

    /** Builds the O5 advertisement UUID embedding a specific (already-paired) PDM id. */
    fun o5AdvertisementUuidForPdmId(pdmId: Long): String =
        "CE1F923D-C539-48EA-7300-0A%08X00".format(pdmId and 0xFFFFFFFFL)

    /** The data characteristic UUID to use for a given [podType] (Dash or O5). */
    fun dataCharacteristicUuid(podType: PodType): String = when (podType) {
        PodType.OMNIPOD_5 -> O5_DATA_CHARACTERISTIC_UUID
        else               -> DASH_DATA_CHARACTERISTIC_UUID
    }

    /** The pre-pairing advertisement UUID to scan for, for a given [podType]. */
    fun unpairedAdvertisementUuid(podType: PodType): String = when (podType) {
        PodType.OMNIPOD_5 -> O5_UNPAIRED_ADVERTISEMENT_UUID
        else               -> DASH_ADVERTISEMENT_UUID
    }
}
