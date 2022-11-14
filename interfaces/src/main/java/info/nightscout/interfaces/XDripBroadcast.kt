package info.nightscout.interfaces

import info.nightscout.database.entities.GlucoseValue
import org.json.JSONArray
import org.json.JSONObject

interface XDripBroadcast {

    fun sendCalibration(bg: Double): Boolean
    fun send(glucoseValue: GlucoseValue)
    fun sendProfile(profileStoreJson: JSONObject)
    fun sendTreatments(addedOrUpdatedTreatments: JSONArray)
    fun sendSgvs(sgvs: JSONArray)
}