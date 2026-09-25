package app.aaps.pump.omnipod.common.bledriver.pod.definition

import app.aaps.core.data.model.BS
import com.google.gson.annotations.SerializedName

enum class BolusType {
    // Alternate "NORMAL" reads pod state persisted by AAPS 3.x, when this field was BS.Type (Gson
    // stores enums by name, so old JSON has "NORMAL" here, not "DEFAULT"). Keep while upgrades from
    // AAPS 3.x are supported.
    @SerializedName(value = "DEFAULT", alternate = ["NORMAL"])
    DEFAULT, SMB, BASAL_CORRECTION, PRIMING;

    // Null for BASAL_CORRECTION: it has no AAPS equivalent and must never be synced as a bolus.
    fun toBolusInfoBolusType(): BS.Type? {
        return when (this) {
            DEFAULT           -> BS.Type.NORMAL
            SMB               -> BS.Type.SMB
            PRIMING           -> BS.Type.PRIMING
            BASAL_CORRECTION  -> null
        }
    }

    companion object {

        fun fromBolusInfoBolusType(type: BS.Type): BolusType {
            return when (type) {
                BS.Type.SMB     -> SMB
                BS.Type.PRIMING -> PRIMING
                BS.Type.NORMAL  -> DEFAULT
            }
        }
    }
}
