package app.aaps.implementation.maintenance.formats

import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.implementation.maintenance.PrefsMetadataKeyImpl
import app.aaps.implementation.maintenance.data.PrefsStatusImpl

/**
 * What an export says about where it came from.
 *
 * This is not decoration. The import screen shows these rows and decides from the flavour and the
 * version whether the file may be imported at all, so a missing or misspelt key is the difference
 * between a backup that restores and one the app refuses. Building it in one place keeps every
 * platform stamping the same six things under the same names.
 *
 * The values themselves are the platform's to find - a device name comes from `UIDevice` on iOS and
 * from `Settings.System` on Android - so they arrive as strings rather than being looked up here.
 */
object ExportMetadata {

    /**
     * @param deviceName what the user calls this phone, shown so they can tell two backups apart.
     * @param createdAt ISO 8601, the only format the reader knows how to turn back into a date.
     * @param version the AAPS version, checked on import.
     * @param flavour the build flavour, checked on import.
     * @param deviceModel the hardware, shown but not checked.
     */
    fun forExport(
        deviceName: String,
        createdAt: String,
        version: String,
        flavour: String,
        deviceModel: String
    ): Map<PrefsMetadataKey, PrefMetadata> = mapOf(
        PrefsMetadataKeyImpl.DEVICE_NAME to PrefMetadata(deviceName, PrefsStatusImpl.OK),
        PrefsMetadataKeyImpl.CREATED_AT to PrefMetadata(createdAt, PrefsStatusImpl.OK),
        PrefsMetadataKeyImpl.AAPS_VERSION to PrefMetadata(version, PrefsStatusImpl.OK),
        PrefsMetadataKeyImpl.AAPS_FLAVOUR to PrefMetadata(flavour, PrefsStatusImpl.OK),
        PrefsMetadataKeyImpl.DEVICE_MODEL to PrefMetadata(deviceModel, PrefsStatusImpl.OK),
        // Says the export is encrypted. The reader replaces this with what it actually found, so it
        // is a claim on the way out and a verdict on the way back in.
        PrefsMetadataKeyImpl.ENCRYPTION to PrefMetadata(ENCRYPTION_ENABLED, PrefsStatusImpl.OK)
    )

    private const val ENCRYPTION_ENABLED = "Enabled"
}
