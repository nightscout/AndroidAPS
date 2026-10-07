package app.aaps.core.data.xdrip

import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.xdrip.XdripSourceResolver.COLLECTION_METHODS

/**
 * Everything AAPS knows about how xDrip names a glucose source.
 *
 * It lives in its own package on purpose. These are facts about another app, read from xDrip master
 * on 2026-10-07, and they go out of date on xDrip's schedule and not ours - so they must be easy to
 * find and re-check. Nothing here is a fact about AAPS, and [SourceSensor] itself stays free of it.
 *
 * Two places need this: the broadcast that xDrip and Juggluco send, and a Nightscout site that xDrip
 * uploads to.
 */
object XdripSourceResolver {

    /** Prefix xDrip puts in front of the collection method in the Nightscout `device` field. */
    private const val DEVICE_PREFIX = "xDrip-"

    /**
     * Resolves a Nightscout `device` value, which may have been written by xDrip.
     *
     * xDrip writes it as `"xDrip-" + <collection method>` and appends the sensor name only when the
     * user turns on a preference that is off by default (`NightscoutUploader.getDeviceString`). So
     * three steps are tried, all of them exact, and anything without the prefix is left to the plain
     * [SourceSensor.fromString]:
     *  1. the value as it is
     *  2. the part after the first space, which is the appended sensor name when present
     *  3. the collection method alone, through [COLLECTION_METHODS]
     *
     * Without step 3 an AAPS client following a Nightscout site fed by xDrip could not identify any
     * reading, because most of those records carry nothing but the method.
     */
    fun fromNightscoutDevice(device: String?): SourceSensor {
        val value = device?.trim()?.takeIf { it.isNotEmpty() } ?: return SourceSensor.UNKNOWN
        SourceSensor.fromString(value).takeIf { it != SourceSensor.UNKNOWN }?.let { return it }
        if (!value.startsWith(DEVICE_PREFIX, ignoreCase = true)) return SourceSensor.UNKNOWN
        SourceSensor.fromString(value.substringAfter(' ', "")).takeIf { it != SourceSensor.UNKNOWN }?.let { return it }
        return COLLECTION_METHODS[value.substringBefore(' ').trim().lowercase()] ?: SourceSensor.UNKNOWN
    }

    /**
     * The xDrip collection methods we can turn into a sensor, keyed in lower case. The method is the
     * internal name of xDrip's own `DexCollectionType`.
     *
     * An unknown method resolves to [SourceSensor.UNKNOWN], so this table going out of date costs us
     * identification and never correctness.
     *
     * Methods are left out on purpose where they do not identify a sensor, so that they stay unknown
     * and keep the flat check:
     *  - `NSFollower`, `WebFollower`, `Follower` and `NSEmulator` relay readings from any source.
     *  - `BluetoothWixel`, `DexbridgeWixel`, `WifiWixel`, `WifiBlueToothWixel` and
     *    `WifiDexbridgeWixel` are Dexcom G4 bridges sending raw uncalibrated data, which has no
     *    sensor here.
     *  - `LibreAlarm` reports both Libre 1 and Libre 2 readings.
     *  - `LimiTTerWifi` only says the reading came over the network, not which bridge produced it.
     *  - `UiBased`, `Mock`, `Manual`, `Disabled` and `None` are not sensor data at all.
     */
    private val COLLECTION_METHODS: Map<String, SourceSensor> = mapOf(
        // Covers G5, G6 and G7 depending on the native mode and the transmitter id, so the vendor is
        // known and the model is not.
        "xdrip-dexcomg5" to SourceSensor.DEXCOM_UNKNOWN,
        "xdrip-dexcomshare" to SourceSensor.DEXCOM_UNKNOWN,
        "xdrip-shfollower" to SourceSensor.DEXCOM_UNKNOWN,
        "xdrip-medtrum" to SourceSensor.MEDTRUM_UNKNOWN,
        "xdrip-clfollower" to SourceSensor.MM_UNKNOWN,
        "xdrip-glupro" to SourceSensor.GLUPRO,
        "xdrip-aidexreceiver" to SourceSensor.AIDEX,
        "xdrip-librereceiver" to SourceSensor.LIBRE_2,
        "xdrip-limitter" to SourceSensor.LIBRE_1,
        "xdrip-librewifi" to SourceSensor.LIBRE_1,
    )
}
