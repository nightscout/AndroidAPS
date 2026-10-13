package app.aaps.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.shared.impl.weardata.ResFileMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for complication data using DataStore with Protocol Buffers
 *
 * Best practices implemented:
 * - Single source of truth for complication data
 * - Asynchronous reads/writes (non-blocking)
 * - Type-safe data access
 * - Corruption handling with recovery
 * - Reactive updates via Flow
 *
 */
@Singleton
class ComplicationDataRepository @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) {

    private val dataStore: DataStore<ComplicationData> = DataStoreFactory.create(
        serializer = ComplicationDataSerializer(aapsLogger),
        produceFile = { context.dataStoreFile("complication_data.pb") }
    )

    /**
     * Reactive Flow of complication data
     * Complications should collect this Flow to get automatic updates
     */
    val complicationData: Flow<ComplicationData> = dataStore.data
        .catch { exception ->
            aapsLogger.error(LTag.WEAR, "Error reading complication data", exception)
            emit(ComplicationData()) // Emit default on error
        }

    /**
     * Update BG data from phone
     * Supports multiple datasets for AAPSClient mode (0=primary, 1=client1, 2=client2)
     */
    suspend fun updateBgData(singleBg: EventData.SingleBg) {
        try {
            dataStore.updateData { current ->
                when (singleBg.dataset) {
                    0 -> current.copy(bgData = singleBg, lastUpdateTimestamp = System.currentTimeMillis())
                    1 -> current.copy(bgData1 = singleBg, lastUpdateTimestamp = System.currentTimeMillis())
                    2 -> current.copy(bgData2 = singleBg, lastUpdateTimestamp = System.currentTimeMillis())
                    else -> {
                        aapsLogger.warn(LTag.WEAR, "Unknown BG dataset ${singleBg.dataset}, ignoring")
                        current
                    }
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Failed to update BG data", e)
        }
    }

    /**
     * Update Status data from phone
     * Supports multiple datasets for AAPSClient mode (0=primary, 1=client1, 2=client2)
     */
    suspend fun updateStatusData(status: EventData.Status) {
        try {
            dataStore.updateData { current ->
                when (status.dataset) {
                    0 -> current.copy(statusData = status, lastUpdateTimestamp = System.currentTimeMillis())
                    1 -> current.copy(statusData1 = status, lastUpdateTimestamp = System.currentTimeMillis())
                    2 -> current.copy(statusData2 = status, lastUpdateTimestamp = System.currentTimeMillis())
                    else -> {
                        aapsLogger.warn(LTag.WEAR, "Unknown Status dataset ${status.dataset}, ignoring")
                        current
                    }
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Failed to update status data", e)
        }
    }

    /**
     * Update Graph data from phone
     */
    suspend fun updateGraphData(graphData: EventData.GraphData) {
        try {
            dataStore.updateData { current ->
                current.copy(
                    graphData = graphData,
                    lastUpdateTimestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Failed to update graph data", e)
        }
    }

    /**
     * Update Treatment data from phone
     */
    suspend fun updateTreatmentData(treatmentData: EventData.TreatmentData) {
        try {
            dataStore.updateData { current ->
                current.copy(
                    treatmentData = treatmentData,
                    lastUpdateTimestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Failed to update treatment data", e)
        }
    }

    /**
     * Store custom watchface data
     * @param customWatchface Main watchface to store
     * @param customWatchfaceFull Optional full version with all resources
     * @param isDefault If true, store as default instead of current
     */
    suspend fun storeCustomWatchface(
        customWatchface: CwfData,
        customWatchfaceFull: CwfData? = null,
        isDefault: Boolean = false
    ) {
        try {
            dataStore.updateData { current ->
                if (isDefault) {
                    current.copy(
                        customWatchfaceDefault = customWatchface,
                        customWatchfaceDefaultFull = customWatchfaceFull
                    )
                } else {
                    current.copy(customWatchface = customWatchface)
                }
            }
            aapsLogger.debug(LTag.WEAR, "Stored custom watchface ${if (isDefault) "(default)" else ""}: ${customWatchface.metadata}")
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Failed to store custom watchface", e)
        }
    }

    /**
     * Update custom watchface metadata only (preserves resources)
     * Used when syncing metadata changes without re-sending all resources
     */
    suspend fun updateCustomWatchfaceMetadata(newMetadata: Map<CwfMetadataKey, String>) {
        try {
            dataStore.updateData { current ->
                current.customWatchface?.let { savedCwf ->
                    // Check if name and version match before updating
                    if (newMetadata[CwfMetadataKey.CWF_NAME] == savedCwf.metadata[CwfMetadataKey.CWF_NAME] &&
                        newMetadata[CwfMetadataKey.CWF_AUTHOR_VERSION] == savedCwf.metadata[CwfMetadataKey.CWF_AUTHOR_VERSION]
                    ) {
                        val updatedCwf = CwfData(savedCwf.json, newMetadata.toMutableMap(), savedCwf.resData)
                        aapsLogger.debug(LTag.WEAR, "Updated custom watchface metadata: $newMetadata")
                        current.copy(customWatchface = updatedCwf)
                    } else {
                        current
                    }
                } ?: current
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Failed to update custom watchface metadata", e)
        }
    }

    /**
     * Reset current watchface to default
     */
    suspend fun setDefaultWatchface() {
        try {
            dataStore.updateData { current ->
                current.customWatchfaceDefault?.let { default ->
                    aapsLogger.debug(LTag.WEAR, "Reset to default watchface")
                    current.copy(customWatchface = default)
                } ?: current
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Failed to set default watchface", e)
        }
    }

    /**
     * Get custom watchface with fallback to default
     * @param isDefault If true, read default instead of current
     * @return CwfData or null if not set
     */
    suspend fun getCustomWatchface(isDefault: Boolean = false): CwfData? {
        return try {
            val current = complicationData.first()
            if (isDefault) {
                current.customWatchfaceDefault
            } else {
                current.customWatchface ?: current.customWatchfaceDefault
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Failed to get custom watchface", e)
            null
        }
    }

    /**
     * Get simplified custom watchface (only main resource file)
     * Used for exporting without external resources
     * @param useDefault If true, read from default storage
     * @return Simplified CwfData or null
     */
    suspend fun getSimplifiedCustomWatchface(useDefault: Boolean = false): CwfData? {
        return try {
            val current = complicationData.first()
            val source = if (useDefault) {
                current.customWatchfaceDefaultFull ?: current.customWatchfaceDefault
            } else {
                current.customWatchface
            }

            source?.let { cwf ->
                // Simplify by keeping only the main CUSTOM_WATCHFACE resource
                cwf.resData[ResFileMap.CUSTOM_WATCHFACE.fileName]?.let { mainRes ->
                    val simplifiedResData = mutableMapOf(ResFileMap.CUSTOM_WATCHFACE.fileName to mainRes)
                    CwfData(cwf.json, cwf.metadata, simplifiedResData)
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Failed to get simplified custom watchface", e)
            null
        }
    }
}

/**
 * Serializer for ComplicationData using Protocol Buffers
 * Handles corruption gracefully by returning default data
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private class ComplicationDataSerializer(
    private val aapsLogger: AAPSLogger
) : Serializer<ComplicationData> {

    override val defaultValue: ComplicationData = ComplicationData()

    override suspend fun readFrom(input: InputStream): ComplicationData {
        return try {
            ProtoBuf.decodeFromByteArray(
                ComplicationData.serializer(),
                input.readBytes()
            )
        } catch (e: SerializationException) {
            aapsLogger.error(LTag.WEAR, "Corrupted complication data, using default", e)
            defaultValue
        }
    }

    override suspend fun writeTo(t: ComplicationData, output: OutputStream) {
        output.write(
            ProtoBuf.encodeToByteArray(
                ComplicationData.serializer(),
                t
            )
        )
    }
}
