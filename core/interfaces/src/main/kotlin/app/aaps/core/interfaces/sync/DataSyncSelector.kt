package app.aaps.core.interfaces.sync

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.DS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import org.json.JSONObject

interface DataSyncSelector {

    interface DataPair {

        val value: Any
        val id: Long
        var confirmed: Boolean
    }

    data class PairTemporaryTarget(override val value: TT, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairGlucoseValue(override val value: GV, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairTherapyEvent(override val value: TE, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairFood(override val value: FD, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairBolus(override val value: BS, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairCarbs(override val value: CA, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairBolusCalculatorResult(override val value: BCR, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairTemporaryBasal(override val value: TB, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairExtendedBolus(override val value: EB, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairProfileSwitch(override val value: PS, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairEffectiveProfileSwitch(override val value: EPS, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairRunningMode(override val value: RM, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairProfileStore(override val value: JSONObject, override val id: Long, override var confirmed: Boolean = false) : DataPair
    data class PairDeviceStatus(override val value: DS, override val id: Long, override var confirmed: Boolean = false) : DataPair

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