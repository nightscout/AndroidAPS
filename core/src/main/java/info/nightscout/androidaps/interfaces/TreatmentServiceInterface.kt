package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.db.Treatment

interface TreatmentServiceInterface {

    fun createOrUpdateMedtronic(treatment: Treatment, fromNightScout: Boolean): UpdateReturn
    fun createOrUpdate(treatment: Treatment): UpdateReturn
}