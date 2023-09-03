package info.nightscout.interfaces.sync

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.DeviceStatus
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.Food
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.TherapyEvent
import org.json.JSONObject

interface DataSyncSelector {

    interface DataPair {

        val value: Any
        val id: Long
        var confirmed: Boolean
    }

    data class PairTemporaryTarget(override val value: TemporaryTarget, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairGlucoseValue(override val value: GlucoseValue, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairTherapyEvent(override val value: TherapyEvent, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairFood(override val value: Food, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairBolus(override val value: Bolus, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairCarbs(override val value: Carbs, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairBolusCalculatorResult(override val value: BolusCalculatorResult, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairTemporaryBasal(override val value: TemporaryBasal, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairExtendedBolus(override val value: ExtendedBolus, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairProfileSwitch(override val value: ProfileSwitch, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairEffectiveProfileSwitch(override val value: EffectiveProfileSwitch, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairOfflineEvent(override val value: OfflineEvent, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairProfileStore(override val value: JSONObject, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairDeviceStatus(override val value: DeviceStatus, override val id: Long, override var confirmed: Boolean = false) : DataPair

    fun queueSize(): Long

    fun resetToNextFullSync()

    suspend fun doUpload()

    /**
     * This function called when new profile is received from NS
     * Plugin should update internal timestamp to not send Profile back as a new/updated
     *
     * @param timestamp received timestamp of profile
     *
     */
    fun profileReceived(timestamp: Long)
}