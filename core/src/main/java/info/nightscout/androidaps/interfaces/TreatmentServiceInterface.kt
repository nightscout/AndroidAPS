package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.db.Treatment

interface TreatmentServiceInterface {

    fun getTreatmentDataFromTime(mills: Long, ascending: Boolean): List<Treatment>
    fun getTreatmentDataFromTime(from: Long, to: Long, ascending: Boolean): List<Treatment>
    fun getTreatmentData(): List<Treatment>
    fun getLastBolus(excludeSMB: Boolean): Treatment?
    fun getLastCarb(): Treatment?
    fun createOrUpdateMedtronic(treatment: Treatment, fromNightScout: Boolean): UpdateReturn
    fun createOrUpdate(treatment: Treatment): UpdateReturn
    fun resetTreatments()
    fun delete(data: Treatment)
    fun update(data: Treatment)
    fun count(): Long
}