package app.aaps.plugins.aps.autotune.data

import app.aaps.core.interfaces.utils.DateUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

@Suppress("SpellCheckingInspection") class PreppedGlucose {

    var crData: List<CRDatum> = ArrayList()
    var csfGlucoseData: List<BGDatum> = ArrayList()
    var isfGlucoseData: List<BGDatum> = ArrayList()
    var basalGlucoseData: List<BGDatum> = ArrayList()
    var diaDeviations: List<DiaDeviation> = ArrayList()
    var peakDeviations: List<PeakDeviation> = ArrayList()
    var from: Long = 0
    lateinit var dateUtil: DateUtil

    // to generate same king of json string than oref0-autotune-prep
    override fun toString(): String {
        return toString(0)
    }

    constructor(from: Long, crData: List<CRDatum>, csfGlucoseData: List<BGDatum>, isfGlucoseData: List<BGDatum>, basalGlucoseData: List<BGDatum>, dateUtil: DateUtil) {
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
        crData = ArrayList()
        csfGlucoseData = ArrayList()
        isfGlucoseData = ArrayList()
        basalGlucoseData = ArrayList()
        try {
            crData = jsonCRDataToList(json.getJSONArray("CRData"))
            csfGlucoseData = jsonGlucoseDataToList(json.getJSONArray("CSFGlucoseData"))
            isfGlucoseData = jsonGlucoseDataToList(json.getJSONArray("ISFGlucoseData"))
            basalGlucoseData = jsonGlucoseDataToList(json.getJSONArray("basalGlucoseData"))
        } catch (_: JSONException) {
        }
    }

    private fun jsonGlucoseDataToList(array: JSONArray): List<BGDatum> {
        val bgData: MutableList<BGDatum> = ArrayList()
        for (index in 0 until array.length()) {
            try {
                val o = array.getJSONObject(index)
                bgData.add(BGDatum(o, dateUtil))
            } catch (_: Exception) {
            }
        }
        return bgData
    }

    private fun jsonCRDataToList(array: JSONArray): List<CRDatum> {
        val crData: MutableList<CRDatum> = ArrayList()
        for (index in 0 until array.length()) {
            try {
                val o = array.getJSONObject(index)
                crData.add(CRDatum(o, dateUtil))
            } catch (_: Exception) {
            }
        }
        return crData
    }

    fun toString(indent: Int): String {
        var jsonString = ""
        val json = JSONObject()
        try {
            val crjson = JSONArray()
            for (crd in crData) {
                crjson.put(crd.toJSON())
            }
            val csfjson = JSONArray()
            for (bgd in csfGlucoseData) {
                csfjson.put(bgd.toJSON(true))
            }
            val isfjson = JSONArray()
            for (bgd in isfGlucoseData) {
                isfjson.put(bgd.toJSON(false))
            }
            val basaljson = JSONArray()
            for (bgd in basalGlucoseData) {
                basaljson.put(bgd.toJSON(false))
            }
            val diajson = JSONArray()
            val peakjson = JSONArray()
            if (diaDeviations.isNotEmpty() || peakDeviations.isNotEmpty()) {
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
            if (diaDeviations.isNotEmpty() || peakDeviations.isNotEmpty()) {
                json.put("diaDeviations", diajson)
                json.put("peakDeviations", peakjson)
            }
            jsonString = if (indent != 0) json.toString(indent) else json.toString()
        } catch (_: JSONException) {
        }
        return jsonString
    }
}