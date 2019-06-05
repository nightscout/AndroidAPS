package info.nightscout.androidaps.plugins.general.tidepool.comm

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.general.tidepool.elements.*
import info.nightscout.androidaps.plugins.general.tidepool.utils.GsonInstance
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.T
import org.slf4j.LoggerFactory
import java.util.*

object UploadChunk {

    private val MAX_UPLOAD_SIZE = T.days(7).msecs() // don't change this

    private val log = LoggerFactory.getLogger(L.TIDEPOOL)

    fun getNext(session: Session): String? {
        session.start = getLastEnd()
        session.end = Math.min(session.start + MAX_UPLOAD_SIZE, DateUtil.now())

        val result = get(session.start, session.end)
        if (result.length < 3) {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("No records in this time period, setting start to best end time")
            setLastEnd(Math.max(session.end, getOldestRecordTimeStamp()))
        }
        return result
    }

    operator fun get(start: Long, end: Long): String {

        if (L.isEnabled(L.TIDEPOOL)) log.debug("Syncing data between: " + DateUtil.dateAndTimeFullString(start) + " -> " + DateUtil.dateAndTimeFullString(end))
        if (end <= start) {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("End is <= start: " + DateUtil.dateAndTimeFullString(start) + " " + DateUtil.dateAndTimeFullString(end))
            return ""
        }
        if (end - start > MAX_UPLOAD_SIZE) {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("More than max range - rejecting")
            return ""
        }

        val records = LinkedList<BaseElement>()

        records.addAll(getTreatments(start, end))
        records.addAll(getBloodTests(start, end))
        records.addAll(getBasals(start, end))
        records.addAll(getBgReadings(start, end))

        return GsonInstance.defaultGsonInstance().toJson(records)
    }

    fun getLastEnd(): Long {
        val result = SP.getLong(R.string.key_tidepool_last_end, 0)
        return Math.max(result, DateUtil.now() - T.months(2).msecs())
    }

    fun setLastEnd(time: Long) {
        if (time > getLastEnd()) {
            SP.putLong(R.string.key_tidepool_last_end, time)
            if (L.isEnabled(L.TIDEPOOL)) log.debug("Updating last end to: " + DateUtil.dateAndTimeFullString(time))
        } else {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("Cannot set last end to: " + DateUtil.dateAndTimeFullString(time) + " vs " + DateUtil.dateAndTimeFullString(getLastEnd()))
        }
    }

    // numeric limits must match max time windows

    private fun getOldestRecordTimeStamp(): Long {
        // TODO we could make sure we include records older than the first bg record for completeness

        val start: Long = 0
        val end = DateUtil.now()

        val bgReadingList = MainApp.getDbHelper().getBgreadingsDataFromTime(start, end, true)
        return if (bgReadingList.size > 0)
            bgReadingList[0].date
        else -1
    }

    internal fun getTreatments(start: Long, end: Long): List<BaseElement> {
        val result = LinkedList<BaseElement>()
        val treatments = TreatmentsPlugin.getPlugin().service.getTreatmentDataFromTime(start, end, true)
        for (treatment in treatments) {
            if (treatment.carbs > 0) {
                result.add(WizardElement(treatment))
            } else if (treatment.insulin > 0) {
                result.add(BolusElement(treatment))
            }
        }
        return result
    }


    internal fun getBloodTests(start: Long, end: Long): List<BloodGlucoseElement> {
        val readings = MainApp.getDbHelper().getCareportalEvents(start, end, true)
        if (L.isEnabled(L.TIDEPOOL))
            log.debug("${readings.size} CPs selected for upload")
        return BloodGlucoseElement.fromCareportalEvents(readings)

    }

    internal fun getBgReadings(start: Long, end: Long): List<SensorGlucoseElement> {
        val readings = MainApp.getDbHelper().getBgreadingsDataFromTime(start, end, true)
        if (L.isEnabled(L.TIDEPOOL))
            log.debug("${readings.size} BGs selected for upload")
        return SensorGlucoseElement.fromBgReadings(readings)
    }

    internal fun getBasals(start: Long, end: Long): List<BasalElement> {
        val tbrs = MainApp.getDbHelper().getTemporaryBasalsDataFromTime(start, end, true)
        if (L.isEnabled(L.TIDEPOOL))
            log.debug("${tbrs.size} TBRs selected for upload")
        return BasalElement.fromTemporaryBasals(tbrs)
    }

}