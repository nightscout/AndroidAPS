package app.aaps.core.interfaces.rx.weardata

import app.aaps.core.interfaces.rx.events.Event
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.Date
import java.util.Objects

@Serializable
sealed class EventData : Event() {

    var sourceNodeId = ""

    fun serialize() = Json.encodeToString(serializer(), this)

    @ExperimentalSerializationApi
    fun serializeByte() = ProtoBuf.encodeToByteArray(serializer(), this)

    companion object {

        fun deserialize(json: String) = try {
            Json.decodeFromString(serializer(), json)
        } catch (_: Exception) {
            Error(System.currentTimeMillis())
        }

        @ExperimentalSerializationApi
        fun deserializeByte(byteArray: ByteArray) = try {
            ProtoBuf.decodeFromByteArray(serializer(), byteArray)
        } catch (_: Exception) {
            Error(System.currentTimeMillis())
        }
    }

    // Mobile <- Wear
    @Serializable
    data class ActionPong(val timeStamp: Long, val apiLevel: Int) : EventData()

    @Serializable
    data class WearException(
        val timeStamp: Long,
        val exception: ByteArray,
        val board: String,
        val fingerprint: String,
        val sdk: String,
        val model: String,
        val manufacturer: String,
        val product: String
    ) : EventData() {

        override fun equals(other: Any?): Boolean =
            when (other) {
                !is WearException -> false
                else              -> timeStamp == other.timeStamp && fingerprint == other.fingerprint
            }

        override fun hashCode(): Int {
            return Objects.hash(timeStamp, fingerprint)
        }
    }

    @Serializable
    data class Error(val timeStamp: Long) : EventData() // ignored

    @Serializable
    data class CancelBolus(val timeStamp: Long) : EventData()

    @Serializable
    data class ActionResendData(val from: String) : EventData()

    @Serializable
    data class ActionPumpStatus(val timeStamp: Long) : EventData()

    @Serializable
    data class ActionLoopStatus(val timeStamp: Long) : EventData()

    @Serializable
    data class ActionLoopStatusDetailed(
        val timeStamp: Long
    ) : EventData()

    @Serializable
    data class LoopStatusResponse(
        val timeStamp: Long,
        val data: LoopStatusData
    ) : EventData()

    @Serializable
    data class ActionTddStatus(val timeStamp: Long) : EventData()

    @Serializable
    data class ActionECarbsPreCheck(val carbs: Int, val carbsTimeShift: Int, val duration: Int) : EventData()

    @Serializable
    data class ActionBolusPreCheck(val insulin: Double, val carbs: Int) : EventData()

    @Serializable
    data class ActionFillPreCheck(val insulin: Double) : EventData()

    @Serializable
    data class ActionFillPresetPreCheck(val button: Int) : EventData()

    @Serializable
    data class ActionProfileSwitchSendInitialData(val timeStamp: Long) : EventData()

    @Serializable
    data class ActionProfileSwitchPreCheck(val timeShift: Int, val percentage: Int, val duration: Int) : EventData()

    @Serializable
    data class ActionWizardPreCheck(val carbs: Int, val percentage: Int) : EventData()

    @Serializable
    data class ActionQuickWizardPreCheck(val guid: String) : EventData()

    @Serializable
    data class ActionUserActionPreCheck(val id: String, val title: String) : EventData()

    @Serializable
    data class ActionUserActionConfirmed(val id: String, val title: String) : EventData()

    @Serializable
    data class ActionScenePreCheck(val id: String, val title: String) : EventData()

    @Serializable
    data class ActionSceneConfirmed(val id: String, val title: String, val bolusId: Long? = null) : EventData()

    @Serializable
    class ActionSceneStop : EventData()

    @Serializable
    class ActionSceneStopPreCheck : EventData()

    @Serializable
    class ActionSceneStopConfirmed : EventData()

    @Serializable
    data class ActiveSceneState(val active: Boolean) : EventData()

    @Serializable
    data class RunningModeRequest(val timeStamp: Long) : EventData()

    @Serializable
    data class RunningModeSelected(val timeStamp: Long, val index: Int, val duration: Int? = null) : EventData()

    /** Wear ✓ on a running-mode change → the master's parked, consume-once [bolusId] (the shared batch confirm path). */
    @Serializable
    data class RunningModeConfirmed(val bolusId: Long) : EventData()

    @Serializable
    data class ActionHeartRate(
        val duration: Long,
        val timestamp: Long,
        val beatsPerMinute: Double,
        val device: String
    ) : EventData() {

        override fun toString() =
            "HR ${beatsPerMinute.toInt()} at ${Date(timestamp)} for ${duration / 1000.0}sec $device"
    }

    @Serializable
    data class ActionStepsRate(
        val duration: Long,
        val timestamp: Long,
        val steps5min: Int,
        val steps10min: Int,
        val steps15min: Int,
        val steps30min: Int,
        val steps60min: Int,
        val steps180min: Int,
        val device: String
    ) : EventData() {

        override fun toString() =
            "STEPS 5min: $steps5min, 10min: $steps10min, 15min: $steps15min, 30min: $steps30min, 60min: $steps60min, 180min: $steps180min at ${Date(timestamp)} for ${duration / 1000.0}sec $device"
    }

    @Serializable
    data class ActionTempTargetPreCheck(
        val command: TempTargetCommand,
        val isMgdl: Boolean = true, val duration: Int = 0, val low: Double = 0.0, val high: Double = 0.0 // manual
    ) : EventData() {

        @Serializable
        enum class TempTargetCommand {

            PRESET_ACTIVITY, PRESET_HYPO, PRESET_EATING, CANCEL, MANUAL
        }

    }

    // Mobile <- Wear return

    /** Wear ✓ on a wizard / quick-wizard bolus → the master's parked, consume-once bolusId ([timeStamp] is the
     *  opaque id field, == the master `wizard.timeStamp`; the wear caller echoes the id `prepareWizard`/`prepareQuickWizard` returned). */
    @Serializable
    data class ActionWizardConfirmed(val timeStamp: Long, val correctionU: Double = 0.0) : EventData()

    @Serializable
    data class ActionTempTargetConfirmed(val bolusId: Long) : EventData()

    @Serializable
    data class ActionBolusConfirmed(val bolusId: Long) : EventData()

    @Serializable
    data class ActionECarbsConfirmed(val bolusId: Long) : EventData()

    @Serializable
    data class ActionFillConfirmed(val insulin: Double) : EventData()

    @Serializable
    data class ActionProfileSwitchConfirmed(val bolusId: Long) : EventData()

    @Serializable
    data class OpenLoopRequestConfirmed(val timeStamp: Long) : EventData()

    @Serializable
    data class RunningModeList(val timeStamp: Long, val states: List<AvailableRunningMode>) : EventData() {

        @Serializable
        data class AvailableRunningMode(
            val state: RunningMode,
            val durations: List<Int>? = null,
            val title: String? = null, // used for FAKE_DIVIDER
        ) {

            @Serializable
            enum class RunningMode {

                // See LoopDialog
                LOOP_OPEN,
                LOOP_LGS,
                LOOP_CLOSED,

                LOOP_DISABLE,

                LOOP_USER_SUSPEND, // 1h, 2h, 3h, 10h
                LOOP_PUMP_SUSPEND,
                LOOP_RESUME,

                PUMP_DISCONNECT, // 15m, 30m, 1h, 2h, 3h
                PUMP_RECONNECT,

                // Returned current statuses
                LOOP_UNKNOWN,
                SUPERBOLUS,
            }
        }
    }

    // Mobile -> Wear
    @Serializable
    data class CancelNotification(val timeStamp: Long) : EventData()

    @Serializable
    data class ActionGetCustomWatchface(
        val customWatchface: ActionSetCustomWatchface,
        val exportFile: Boolean = false,
        val withDate: Boolean = true
    ) : EventData()

    @Serializable
    data class ActionPing(val timeStamp: Long) : EventData()

    @Serializable
    data class OpenSettings(val timeStamp: Long) : EventData()

    @Serializable
    data class BolusProgress(val percent: Int, val status: String) : EventData()

    interface EventDataSet {

        var dataset: Int
    }

    @Serializable
    data class SingleBg(
        override var dataset: Int,
        var timeStamp: Long,
        val sgvString: String = "---",
        val glucoseUnits: String = "-",
        val slopeArrow: String = "--",
        val delta: String = "--",
        val deltaDetailed: String = "--",
        val avgDelta: String = "--",
        val avgDeltaDetailed: String = "--",
        val sgvLevel: Long = 0,
        val sgv: Double,
        val high: Double, // highLine
        val low: Double, // lowLine
        val color: Int = 0,
        val deltaMgdl: Double? = null,
        val avgDeltaMgdl: Double? = null,
        val id: Int = 0
    ) : EventDataSet, EventData(), Comparable<SingleBg> {

        override fun equals(other: Any?): Boolean =
            when {
                other !is SingleBg   -> false
                color != other.color -> false
                else                 -> timeStamp == other.timeStamp
            }

        override fun hashCode(): Int {
            return Objects.hash(timeStamp, color)
        }

        override fun compareTo(other: SingleBg): Int {
            // reverse order endTime get latest first
            if (this.timeStamp < other.timeStamp) return 1
            return if (this.timeStamp > other.timeStamp) -1 else 0
        }
    }

    @Serializable
    data class GraphData(
        val entries: ArrayList<SingleBg>
    ) : EventData()

    @Serializable
    data class TreatmentData(
        val temps: ArrayList<TempBasal>,
        val basals: ArrayList<Basal>,
        val boluses: ArrayList<Treatment>,
        val predictions: ArrayList<SingleBg>
    ) : EventData() {

        @Serializable
        data class TempBasal(
            val startTime: Long,
            val startBasal: Double,
            val endTime: Long,
            val endBasal: Double,
            val amount: Double
        )

        @Serializable
        data class Basal(
            val startTime: Long,
            val endTime: Long,
            val amount: Double
        )

        @Serializable
        data class Treatment(
            val date: Long,
            val bolus: Double,
            val carbs: Double,
            val isSMB: Boolean,
            val isValid: Boolean,
        )
    }

    @Serializable
    data class Status(
        override var dataset: Int,
        val externalStatus: String,
        val iobSum: String,
        val iobDetail: String,
        val cob: String,
        val currentBasal: String,
        val battery: String,
        val rigBattery: String,
        val openApsStatus: Long,
        val bgi: String,
        val batteryLevel: Int,
        val patientName: String = "",
        val tempTarget: String,
        val tempTargetLevel: Int,
        val tempTargetDuration: Long = -1L,
        val reservoirString: String,
        val reservoir: Double,
        val reservoirLevel: Int
    ) : EventData(), EventDataSet

    @Serializable
    data class Preferences(
        val timeStamp: Long,
        val wearControl: Boolean,
        val unitsMgdl: Boolean,
        val bolusPercentage: Int,
        val maxCarbs: Int,
        val maxBolus: Double,
        val insulinButtonIncrement1: Double,
        val insulinButtonIncrement2: Double,
        val carbsButtonIncrement1: Int,
        val carbsButtonIncrement2: Int
    ) : EventData()

    @Serializable
    data class QuickWizard(
        val entries: ArrayList<QuickWizardEntry>
    ) : EventData() {

        @Serializable
        data class QuickWizardEntry(
            val guid: String,
            val buttonText: String,
            val carbs: Int,
            val validFrom: Int,
            val validTo: Int,
            val lastUsed: Long = 0L,
            val mode: Int = 0,
            val insulin: Double = 0.0
        ) : EventData()
    }

    @Serializable
    data class UserAction(
        val entries: ArrayList<UserActionEntry>
    ) : EventData() {

        @Serializable
        data class UserActionEntry(
            val timeStamp: Long,
            // Stable UUID of the AutomationEvent — survives reload / NS sync, unlike the prior
            // identity-hashCode-as-Int which lost the binding whenever the master reconstructed
            // its event list and silently broke wear taps.
            val id: String,
            val title: String
        ) : EventData()
    }

    @Serializable
    data class SceneList(
        val entries: ArrayList<SceneEntry>
    ) : EventData() {

        @Serializable
        data class SceneEntry(
            val timeStamp: Long,
            val id: String,
            val title: String
        ) : EventData()
    }

    @Serializable
    data class ActionSetCustomWatchface(val customWatchfaceData: CwfData) : EventData()

    @Serializable
    data class ActionUpdateCustomWatchface(val customWatchfaceData: CwfData) : EventData()

    @Serializable
    data class ActionrequestCustomWatchface(val exportFile: Boolean) : EventData()

    @Serializable
    data class ActionrequestSetDefaultWatchface(val timeStamp: Long) : EventData()

    @Serializable
    data class ActionProfileSwitchOpenActivity(val timeShift: Int, val percentage: Int, val duration: Int) : EventData()

    @Serializable
    data class OpenLoopRequest(val title: String, val message: String, val returnCommand: EventData?) : EventData()

    /** One master-authored confirmation row the watch renders verbatim. [role] is a [ConfirmationRole] name (color hint). */
    @Serializable
    data class ConfirmActionLine(val role: String, val text: String)

    /**
     * Raw bolus-wizard calculation breakdown sent alongside [ConfirmAction] for wizard/quick-wizard boluses.
     * The watch renders a dedicated "Calculations" page so the user can inspect the full dose breakdown before
     * confirming. Absent (null) for non-wizard actions (TT, PS, RM, scene, batch-only bolus/carbs).
     * All insulin values are in the user's units (U); [sens] and [ic] are in profile units.
     */
    @Serializable
    data class WizardDetail(
        val totalInsulin: Double,
        val unclampedInsulin: Double = totalInsulin,
        val carbs: Int,
        val insulinFromBG: Double,
        val insulinFromTrend: Double,
        val insulinFromCOB: Double,
        val insulinFromCarbs: Double,
        val insulinFromBolusIOB: Double,
        val insulinFromBasalIOB: Double,
        val includeBolusIOB: Boolean,
        val includeBasalIOB: Boolean,
        val percentageCorrection: Int,
        val cob: Double,
        /** Formatted TT target string in profile units (e.g. "5.5" or "5.0-5.5"), null when no TT was used. */
        val tempTargetLabel: String?,
        val ic: Double,
        val sens: Double,
        val eCarbsGrams: Int = 0,
        val eCarbsDelayMinutes: Int = 0,
        val eCarbsDurationHours: Int = 0,
        val carbTimeMinutes: Int = 0,
        val alarm: Boolean = false,
        val maxBolus: Double = 0.0,
        val bolusStep: Double = 0.0,
    )

    @Serializable // returnCommand is sent back to Mobile after confirmation
    data class ConfirmAction(
        val title: String,
        val message: String,
        val returnCommand: EventData?,
        // Master-authored, color-coded confirmation rows (bolus / carbs / eCarbs / temp target / profile switch /
        // running mode): the watch renders these verbatim, the same lines the phone dialog + every client show.
        val lines: List<ConfirmActionLine> = emptyList(),
        // [deferConfirm] = the commit is a CLIENT→master round-trip (the watch is paired to an AAPSCLIENT, so every
        // action — bolus/wizard/TT/PS/RM/eCarbs — is relayed and executed on the master): the watch must NOT flash the
        // local success animation on ✓, but instead show the "contacting master" spinner and wait for the master's real
        // terminal ([RemoteDelivered] = success, or an error [ConfirmAction]). False = a master-paired watch (executes
        // locally, no relay) → the watch shows success immediately as before. Set from config.AAPSCLIENT, so it is
        // role-based, not per-action.
        val deferConfirm: Boolean = false,
        // Optional wizard calculation breakdown: populated for bolus-wizard and quick-wizard prepares, null for all
        // other actions. The watch shows an extra "Calculations" page before the confirm page when present.
        val wizardDetail: WizardDetail? = null,
    ) : EventData()

    /**
     * Mobile→Wear: show a transient "Contacting master…" spinner while a CLIENT→master round-trip is in flight (the
     * watch-on-client insulin relay). Dismissed when the resolving [ConfirmAction] (prepare lines / error) or
     * [RemoteDelivered] (commit success) arrives, or by the spinner's own timeout. Emitted only on an AAPSCLIENT.
     */
    @Serializable
    data object ContactingMaster : EventData()

    /**
     * Mobile→Wear: terminal for a deferred (relayed) commit that the master APPLIED — the watch shows its success
     * animation now (it deferred it on ✓). Only sent on an AAPSCLIENT; a master-paired watch shows success locally.
     */
    @Serializable
    data object RemoteDelivered : EventData()

    @Serializable
    data class SnoozeAlert(val timeStamp: Long) : EventData()

    // Wear -> Wear (workaround)
    @Serializable
    data class RunningModePreSelect(
        val timeStamp: Long,
        val stateIndex: Int,
        val durations: List<Int>,
        val title: String = ""
    ) : EventData()
}
