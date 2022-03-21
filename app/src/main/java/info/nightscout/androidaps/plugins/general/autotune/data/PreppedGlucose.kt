package info.nightscout.androidaps.plugins.general.autotune.data

import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class PreppedGlucose {

    var crData: List<CRDatum>? = ArrayList()
    var csfGlucoseData: List<BGDatum>? = ArrayList()
    var isfGlucoseData: List<BGDatum>? = ArrayList()
    var basalGlucoseData: List<BGDatum>? = ArrayList()
    var diaDeviations: List<DiaDatum> = ArrayList()
    var peakDeviations: List<PeakDatum> = ArrayList()
    var from: Long = 0
    lateinit var dateUtil: DateUtil

    // to generate same king of json string than oref0-autotune-prep
    override fun toString(): String {
        return toString(0)
    }

    constructor(from: Long, crData: List<CRDatum>?, csfGlucoseData: List<BGDatum>?, isfGlucoseData: List<BGDatum>?, basalGlucoseData: List<BGDatum>?, dateUtil: DateUtil) {
        this.from = from
        this.crData = crData
        this.csfGlucoseData = csfGlucoseData
        this.isfGlucoseData = isfGlucoseData
        this.basalGlucoseData = basalGlucoseData
        this.dateUtil = dateUtil
    }

    constructor(json: JSONObject?, dateUtil: DateUtil) {
        if (json == null) return
        this.dateUtil = dateUtil
        crData = null
        csfGlucoseData = null
        isfGlucoseData = null
        basalGlucoseData = null
        try {
            crData = JsonCRDataToList(json.getJSONArray("CRData"))
            csfGlucoseData = JsonGlucoseDataToList(json.getJSONArray("CSFGlucoseData"))
            isfGlucoseData = JsonGlucoseDataToList(json.getJSONArray("ISFGlucoseData"))
            basalGlucoseData = JsonGlucoseDataToList(json.getJSONArray("basalGlucoseData"))
        } catch (e: JSONException) {
        }
    }

    private fun JsonGlucoseDataToList(array: JSONArray): List<BGDatum> {
        val bgData: MutableList<BGDatum> = ArrayList()
        for (index in 0 until array.length()) {
            try {
                val o = array.getJSONObject(index)
                bgData.add(BGDatum(o, dateUtil))
            } catch (e: Exception) {
            }
        }
        return bgData
    }

    private fun JsonCRDataToList(array: JSONArray): List<CRDatum> {
        val crData: MutableList<CRDatum> = ArrayList()
        for (index in 0 until array.length()) {
            try {
                val o = array.getJSONObject(index)
                crData.add(CRDatum(o, dateUtil))
            } catch (e: Exception) {
            }
        }
        return crData
    }

    fun toString(indent: Int): String {
        var jsonString = ""
        val json = JSONObject()
        try {
            val crjson = JSONArray()
            for (crd in crData!!) {
                crjson.put(crd.toJSON())
            }
            val csfjson = JSONArray()
            for (bgd in csfGlucoseData!!) {
                csfjson.put(bgd.toJSON(true))
            }
            val isfjson = JSONArray()
            for (bgd in isfGlucoseData!!) {
                isfjson.put(bgd.toJSON(false))
            }
            val basaljson = JSONArray()
            for (bgd in basalGlucoseData!!) {
                basaljson.put(bgd.toJSON(false))
            }
            val diajson = JSONArray()
            val peakjson = JSONArray()
            if (diaDeviations.size > 0 || peakDeviations.size > 0) {
                for (diad in diaDeviations) {
                    diajson.put(diad.toJSON())
                }
                for (peakd in peakDeviations) {
                    peakjson.put(peakd.toJSON())
                }
            }
            json.put("CRData", crjson)
            json.put("CSFGlucoseData", csfjson)
            json.put("ISFGlucoseData", isfjson)
            json.put("basalGlucoseData", basaljson)
            if (diaDeviations.size > 0 || peakDeviations.size > 0) {
                json.put("diaDeviations", diajson)
                json.put("peakDeviations", peakjson)
            }
            jsonString = if (indent != 0) json.toString(indent) else json.toString()
        } catch (e: JSONException) {
        }
        return jsonString
    }

    fun equals(obj: PreppedGlucose): Boolean {
        var isEqual = true
        if (crData!!.size == obj.crData!!.size) {
            for (index in crData!!.indices) if (!crData!![index].equals(obj.crData!![index])) isEqual = false
        } else isEqual = false
        if (csfGlucoseData!!.size == obj.csfGlucoseData!!.size) {
            for (index in csfGlucoseData!!.indices) if (!csfGlucoseData!![index].equals(obj.csfGlucoseData!![index])) isEqual = false
        } else isEqual = false
        if (isfGlucoseData!!.size == obj.isfGlucoseData!!.size) {
            for (index in isfGlucoseData!!.indices) if (!isfGlucoseData!![index].equals(obj.isfGlucoseData!![index])) isEqual = false
        } else isEqual = false
        if (basalGlucoseData!!.size == obj.basalGlucoseData!!.size) {
            for (index in basalGlucoseData!!.indices) if (!basalGlucoseData!![index].equals(obj.basalGlucoseData!![index])) isEqual = false
        } else isEqual = false
        if (diaDeviations.size == obj.diaDeviations.size) {
            for (index in diaDeviations.indices) if (!diaDeviations[index].equals(obj.diaDeviations[index])) isEqual = false
        } else isEqual = false
        if (peakDeviations.size == obj.peakDeviations.size) {
            for (index in peakDeviations.indices) if (!peakDeviations[index].equals(obj.peakDeviations[index])) isEqual = false
        } else isEqual = false
        return isEqual
    }
}