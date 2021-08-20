package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.Iob
import info.nightscout.androidaps.db.Treatment
import org.json.JSONObject

interface InsulinInterface : ConfigExportImportInterface{

    enum class InsulinType(val value: Int) {
        UNKNOWN(-1),
        // int FASTACTINGINSULIN = 0; // old model no longer available
        // int FASTACTINGINSULINPROLONGED = 1; // old model no longer available
        OREF_RAPID_ACTING(2),
        OREF_ULTRA_RAPID_ACTING(3),
        OREF_FREE_PEAK(4),
        OREF_LYUMJEV(5);

        companion object {
            private val map = values().associateBy(InsulinType::value)
            fun fromInt(type: Int) = map[type]
        }
    }

    val id: InsulinType
    val friendlyName: String
    val comment: String
    val dia: Double

    fun iobCalcForTreatment(treatment: Treatment, time: Long, dia: Double): Iob
}