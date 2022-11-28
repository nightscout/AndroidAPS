package info.nightscout.rx.weardata

import info.nightscout.rx.events.Event
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Objects

@Serializable
sealed class EventData : Event() {

    var sourceNodeId = ""

    fun serialize() = Json.encodeToString(serializer(), this)

    companion object {

        fun deserialize(json: String) = try {
            Json.decodeFromString(serializer(), json)
        } catch (ignored: Exception) {
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
    data class ActionProfileSwitchPreCheck(val timeShift: Int, val percentage: Int) : EventData()

    @Serializable
    data class ActionWizardPreCheck(val carbs: Int, val percentage: Int) : EventData()

    @Serializable
    data class ActionQuickWizardPreCheck(val guid: String) : EventData()

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

    @Serializable
    data class ActionWizardConfirmed(val timeStamp: Long) : EventData()

    @Serializable
    data class ActionTempTargetConfirmed(val isMgdl: Boolean = true, val duration: Int = 0, val low: Double = 0.0, val high: Double = 0.0) : EventData()

    @Serializable
    data class ActionBolusConfirmed(val insulin: Double, val carbs: Int) : EventData()

    @Serializable
    data class ActionECarbsConfirmed(val carbs: Int, val carbsTime: Long, val duration: Int) : EventData()

    @Serializable
    data class ActionFillConfirmed(val insulin: Double) : EventData()

    @Serializable
    data class ActionProfileSwitchConfirmed(val timeShift: Int, val percentage: Int) : EventData()

    @Serializable
    data class OpenLoopRequestConfirmed(val timeStamp: Long) : EventData()

    // Mobile -> Wear
    @Serializable
    data class CancelNotification(val timeStamp: Long) : EventData()

    @Serializable
    data class ActionPing(val timeStamp: Long) : EventData()

    @Serializable
    data class OpenSettings(val timeStamp: Long) : EventData()

    @Serializable
    data class BolusProgress(val percent: Int, val status: String) : EventData()

    @Serializable
    data class SingleBg @JvmOverloads constructor(
        var timeStamp: Long,
        val sgvString: String = "---",
        val glucoseUnits: String = "-",
        val slopeArrow: String = "--",
        val delta: String = "--",
        val avgDelta: String = "--",
        val sgvLevel: Long = 0,
        val sgv: Double,
        val high: Double, // highLine
        val low: Double, // lowLine
        val color: Int = 0
    ) : EventData(), Comparable<SingleBg> {

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
        val externalStatus: String,
        val iobSum: String,
        val iobDetail: String,
        val detailedIob: Boolean,
        val cob: String,
        val currentBasal: String,
        val battery: String,
        val rigBattery: String,
        val openApsStatus: Long,
        val bgi: String,
        val showBgi: Boolean,
        val batteryLevel: Int
    ) : EventData()

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
            val validTo: Int
        ) : EventData()
    }

    @Serializable
    data class ActionProfileSwitchOpenActivity(val timeShift: Int, val percentage: Int) : EventData()

    @Serializable
    data class OpenLoopRequest(val title: String, val message: String, val returnCommand: EventData?) : EventData()

    @Serializable // returnCommand is sent back to Mobile after confirmation
    data class ConfirmAction(val title: String, val message: String, val returnCommand: EventData?) : EventData()

    @Serializable
    data class SnoozeAlert(val timeStamp: Long) : EventData()
}
