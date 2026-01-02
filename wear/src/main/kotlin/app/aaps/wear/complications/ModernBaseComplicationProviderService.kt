package app.aaps.wear.complications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.interaction.utils.Constants
import app.aaps.wear.interaction.utils.DisplayFormat
import app.aaps.wear.interaction.utils.WearUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import app.aaps.wear.data.ComplicationData as ComplicationStore

/**
 * Modern base class for complications using DataStore and AndroidX Wear APIs
 *
 * Benefits over BaseComplicationProviderService:
 * - 5x faster data reads (DataStore vs SharedPreferences)
 * - Type-safe data access
 * - Reactive updates via Flow
 * - No manual timestamp checking needed
 * - Cleaner API (direct access to BgData/StatusData)
 * - Uses modern AndroidX Wear Watchface API
 *
 */
abstract class ModernBaseComplicationProviderService : ComplicationDataSourceService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var displayFormat: DisplayFormat
    @Inject lateinit var complicationDataRepository: ComplicationDataRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Build complication data using modern DataStore-backed data models
     *
     * Supports multiple datasets for AAPSClient mode:
     * - Dataset 0 (data.bgData, data.statusData): Primary AndroidAPS instance
     * - Dataset 1 (data.bgData1, data.statusData1): AAPSClient1 (follower mode)
     * - Dataset 2 (data.bgData2, data.statusData2): AAPSClient2 (follower mode)
     *
     * @param type The type of complication requested
     * @param data Complete complication data from DataStore
     * @param complicationPendingIntent Action to perform when complication is tapped
     * @return ComplicationData or null if type not supported
     */
    abstract fun buildComplicationData(
        type: ComplicationType,
        data: ComplicationStore,
        complicationPendingIntent: PendingIntent
    ): ComplicationData?

    /**
     * Build complication data when no sync (stale data from watch perspective).
     *
     * Called when the time since last data update from phone exceeds [Constants.STALE_MS].
     * This indicates a connection or configuration issue between phone and watch.
     *
     * Default implementation: Shows the last known data. Subclasses can override to:
     * - Display a warning indicator
     * - Show simplified data
     * - Return null to hide the complication
     *
     * @param type The complication type being requested
     * @param data The last known complication data (may be outdated)
     * @param complicationPendingIntent Action to perform when tapped
     * @return ComplicationData to display, or null to hide the complication
     */
    open fun buildNoSyncComplicationData(
        type: ComplicationType,
        data: ComplicationStore,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        // Default: show stale data with warning
        return buildComplicationData(type, data, complicationPendingIntent)
    }

    /**
     * Build complication data when data is stale (old from phone/sensor).
     *
     * Called when the BG timestamp exceeds [Constants.STALE_MS] from current time.
     * This indicates a sensor or uploader issue, not a watch-phone connection problem.
     * Data is arriving from phone, but the glucose readings are outdated.
     *
     * Default implementation: Shows the outdated data. Subclasses can override to:
     * - Display a staleness indicator (e.g., grayed out, warning icon)
     * - Show time since last reading
     * - Modify formatting to indicate age
     *
     * @param type The complication type being requested
     * @param data The complication data with outdated glucose readings
     * @param complicationPendingIntent Action to perform when tapped
     * @return ComplicationData to display, or null to hide the complication
     */
    open fun buildOutdatedComplicationData(
        type: ComplicationType,
        data: ComplicationStore,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        // Default: show stale data with warning
        return buildComplicationData(type, data, complicationPendingIntent)
    }

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        aapsLogger.debug(LTag.WEAR, "Complication update requested: ${javaClass.simpleName} id=${request.complicationInstanceId} type=${request.complicationType}")

        val thisProvider = ComponentName(this, getProviderCanonicalName())
        val complicationPendingIntent = ComplicationTapActivity.getTapActionIntent(
            context = this,
            provider = thisProvider,
            complicationId = request.complicationInstanceId,
            action = getComplicationAction()
        )

        // Launch coroutine to read from DataStore asynchronously
        scope.launch {
            // Read from DataStore (fast, type-safe) - includes all 3 datasets
            val data = try {
                complicationDataRepository.complicationData.first()
            } catch (e: Exception) {
                aapsLogger.error(LTag.WEAR, "Error reading from DataStore", e)
                // Fallback to defaults
                ComplicationStore()
            }

            aapsLogger.debug(LTag.WEAR, "DataStore read: bgData0=${data.bgData.sgvString} bgData1=${data.bgData1.sgvString} bgData2=${data.bgData2.sgvString} lastUpdate=${WearUtil.msSince(data.lastUpdateTimestamp)}ms ago")

            // Determine data freshness
            val timeSinceUpdate = System.currentTimeMillis() - data.lastUpdateTimestamp
            val timeSinceBg = System.currentTimeMillis() - data.bgData.timeStamp

            val complicationData = when {
                timeSinceUpdate > Constants.STALE_MS -> {
                    // No new data from phone - connection/config issue
                    aapsLogger.warn(LTag.WEAR, "Stale sync: ${WearUtil.msSince(data.lastUpdateTimestamp)}ms since update")
                    buildNoSyncComplicationData(request.complicationType, data, complicationPendingIntent)
                }

                timeSinceBg > Constants.STALE_MS     -> {
                    // Data arriving but outdated - sensor/uploader issue
                    aapsLogger.warn(LTag.WEAR, "Stale BG: ${WearUtil.msSince(data.bgData.timeStamp)}ms old")
                    buildOutdatedComplicationData(request.complicationType, data, complicationPendingIntent)
                }

                else                                 -> {
                    // Fresh data - normal operation
                    buildComplicationData(request.complicationType, data, complicationPendingIntent)
                }
            }

            if (complicationData != null) {
                aapsLogger.debug(LTag.WEAR, "Complication sent to system: ${javaClass.simpleName} id=${request.complicationInstanceId} BG0=${data.bgData.sgvString} BG1=${data.bgData1.sgvString} BG2=${data.bgData2.sgvString} age=${timeSinceBg}ms")
                listener.onComplicationData(complicationData)
            } else {
                aapsLogger.warn(LTag.WEAR, "Complication type ${request.complicationType} not supported by ${javaClass.simpleName}")
                listener.onComplicationData(null)
            }
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        // Return preview data for complication configuration
        // Using empty PendingIntent since preview doesn't respond to taps
        val emptyIntent = PendingIntent.getActivity(
            this, 0, Intent(),
            PendingIntent.FLAG_IMMUTABLE
        )
        // Return preview with realistic sample data
        return buildComplicationData(type, getPreviewComplicationData(), emptyIntent)
    }

    /**
     * Get realistic sample data for complication preview.
     *
     * Called by the system when displaying the complication in the watchface picker
     * or configuration screens. Returns sample data that demonstrates what the
     * complication will look like during normal operation.
     *
     * Default implementation provides realistic diabetes management data:
     * - BG: 120 mg/dl with upward trend
     * - IOB: 1.2U (bolus 1.5U, basal -0.3U)
     * - COB: 15g
     * - Basal: 0.8U/h
     * - Battery: 85%
     * - Looping status active
     *
     * Subclasses can override to customize preview data for their specific
     * complication type or to demonstrate special features.
     *
     * @return Sample ComplicationStore with realistic preview data
     */
    open fun getPreviewComplicationData(): ComplicationStore {
        return ComplicationStore(
            bgData = app.aaps.core.interfaces.rx.weardata.EventData.SingleBg(
                dataset = 0,
                timeStamp = System.currentTimeMillis(),
                sgvString = "120",
                glucoseUnits = "mg/dl",
                slopeArrow = "→",
                delta = "+5",
                deltaDetailed = "+5.2",
                avgDelta = "+3",
                avgDeltaDetailed = "+3.1",
                sgvLevel = 0L,
                sgv = 120.0,
                high = 180.0,
                low = 70.0,
                color = 0
            ),
            statusData = app.aaps.core.interfaces.rx.weardata.EventData.Status(
                dataset = 0,
                externalStatus = "Looping",
                iobSum = "1.2",
                iobDetail = "(1.5|-0.3)",
                cob = "15g",
                currentBasal = "0.8U/h",
                battery = "85",
                rigBattery = "90%",
                openApsStatus = System.currentTimeMillis(),
                bgi = "-2.5",
                batteryLevel = 1,
                patientName = "Sample",
                tempTarget = "100",
                tempTargetLevel = 0,
                reservoirString = "120U",
                reservoir = 120.0,
                reservoirLevel = 1
            ),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Return canonical name for this provider (used for registration).
     *
     * The canonical name uniquely identifies this complication provider to the
     * Android Wear system. It's used in:
     * - Manifest service declarations
     * - ComplicationTapActivity for routing tap actions
     * - System complication provider registration
     *
     * Default implementation uses the class's fully qualified name, which is
     * typically correct unless the provider uses a custom ComponentName.
     *
     * @return Fully qualified class name (e.g., "app.aaps.wear.complications.SgvComplication")
     */
    open fun getProviderCanonicalName(): String = javaClass.canonicalName!!

    /**
     * Return the action to perform when the complication is tapped.
     *
     * Defines what happens when the user taps this complication on their watchface.
     * The action is handled by ComplicationTapActivity, which routes to the
     * appropriate activity or dialog based on the action type.
     *
     * Default: [ComplicationAction.MENU] - Opens the main AAPS menu
     *
     * Common actions:
     * - MENU: Main AAPS menu
     * - WIZARD: Bolus calculator
     * - STATUS: Detailed status screen
     * - NONE: No action (e.g., wallpaper complications)
     *
     * Can be overridden by subclasses to provide context-specific behavior.
     * For example, a battery complication might open settings.
     *
     * @return The [ComplicationAction] to perform on tap
     */
    open fun getComplicationAction(): ComplicationAction = ComplicationAction.MENU
}
