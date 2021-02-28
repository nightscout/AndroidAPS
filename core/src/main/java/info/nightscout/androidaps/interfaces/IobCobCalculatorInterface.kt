package info.nightscout.androidaps.interfaces

import androidx.collection.LongSparseArray
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData

interface IobCobCalculatorInterface {

    val dataLock: Any
    var bgReadings: List<GlucoseValue>

    fun getAutosensDataTable(): LongSparseArray<AutosensData>
    fun calculateIobArrayInDia(profile: Profile): Array<IobTotal>
    fun lastDataTime(): String
    fun getAutosensData(fromTime: Long): AutosensData?
    fun getLastAutosensData(reason: String): AutosensData?
    fun getCobInfo(_synchronized: Boolean, reason: String): CobInfo
    fun calculateFromTreatmentsAndTempsSynchronized(time: Long, profile: Profile?): IobTotal
}