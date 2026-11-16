package app.aaps.plugins.aps.autotune.data

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.aps.autotune.OrgJsonText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    /** Reads an oref0 prep file. A list that is missing or not a list ends the reading, as `org.json` did. */
    constructor(json: JsonObject?, dateUtil: DateUtil) {
        if (json == null) return
        this.dateUtil = dateUtil
        crData = ArrayList()
        csfGlucoseData = ArrayList()
        isfGlucoseData = ArrayList()
        basalGlucoseData = ArrayList()
        try {
            crData = jsonCRDataToList(json.array("CRData"))
            csfGlucoseData = jsonGlucoseDataToList(json.array("CSFGlucoseData"))
            isfGlucoseData = jsonGlucoseDataToList(json.array("ISFGlucoseData"))
            basalGlucoseData = jsonGlucoseDataToList(json.array("basalGlucoseData"))
        } catch (_: OrgJsonReadException) {
        }
    }

    private fun jsonGlucoseDataToList(array: JsonArray): List<BGDatum> {
        val bgData: MutableList<BGDatum> = ArrayList()
        for (index in array.indices) {
            try {
                val o = array.obj(index)
                bgData.add(BGDatum(o, dateUtil))
            } catch (_: Exception) {
            }
        }
        return bgData
    }

    private fun jsonCRDataToList(array: JsonArray): List<CRDatum> {
        val crData: MutableList<CRDatum> = ArrayList()
        for (index in array.indices) {
            try {
                val o = array.obj(index)
                crData.add(CRDatum(o, dateUtil))
            } catch (_: Exception) {
            }
        }
        return crData
    }

    /** The text of an oref0 prep file, written as `org.json` wrote it. See [OrgJsonText]. */
    fun toString(indent: Int): String {
        val json = buildJsonObject {
            put("CRData", buildJsonArray { for (crd in crData) add(crd.toJSON()) })
            put("CSFGlucoseData", buildJsonArray { for (bgd in csfGlucoseData) add(bgd.toJSON(true)) })
            put("ISFGlucoseData", buildJsonArray { for (bgd in isfGlucoseData) add(bgd.toJSON(false)) })
            put("basalGlucoseData", buildJsonArray { for (bgd in basalGlucoseData) add(bgd.toJSON(false)) })
            if (diaDeviations.isNotEmpty() || peakDeviations.isNotEmpty()) {
                put("diaDeviations", buildJsonArray { for (diad in diaDeviations) add(diad.toJSON()) })
                put("peakDeviations", buildJsonArray { for (peakd in peakDeviations) add(peakd.toJSON()) })
            }
        }
        return OrgJsonText.render(json, indent)
    }
}
