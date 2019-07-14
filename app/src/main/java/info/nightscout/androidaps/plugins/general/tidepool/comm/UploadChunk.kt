package info.nightscout.androidaps.plugins.general.tidepool.comm

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.tidepool.elements.*
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolStatus
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

    fun getNext(session: Session?): String? {
        if (session == null)
            return null

        session.start = TidepoolUploader.getLastEnd()
        session.end = Math.min(session.start + MAX_UPLOAD_SIZE, DateUtil.now())

        val result = get(session.start, session.end)
        if (result.length < 3) {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("No records in this time period, setting start to best end time")
            TidepoolUploader.setLastEnd(Math.max(session.end, getOldestRecordTimeStamp()))
        }
        return result
    }

    operator fun get(start: Long, end: Long): String {

        if (L.isEnabled(L.TIDEPOOL)) log.debug("Syncing data between: " + DateUtil.dateAndTimeString(start) + " -> " + DateUtil.dateAndTimeString(end))
        if (end <= start) {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("End is <= start: " + DateUtil.dateAndTimeString(start) + " " + DateUtil.dateAndTimeString(end))
            return ""
        }
        if (end - start > MAX_UPLOAD_SIZE) {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("More than max range - rejecting")
            return ""
        }

        val records = LinkedList<BaseElement>()

        if (SP.getBoolean(R.string.key_tidepool_upload_bolus, true))
            records.addAll(getTreatments(start, end))
        if (SP.getBoolean(R.string.key_tidepool_upload_bg, true))
            records.addAll(getBloodTests(start, end))
        if (SP.getBoolean(R.string.key_tidepool_upload_tbr, true))
            records.addAll(getBasals(start, end))
        if (SP.getBoolean(R.string.key_tidepool_upload_cgm, true))
            records.addAll(getBgReadings(start, end))
        if (SP.getBoolean(R.string.key_tidepool_upload_profile, true))
            records.addAll(getProfiles(start, end))

        return GsonInstance.defaultGsonInstance().toJson(records)
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

    private fun getTreatments(start: Long, end: Long): List<BaseElement> {
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

    private fun getBloodTests(start: Long, end: Long): List<BloodGlucoseElement> {
        val readings = MainApp.getDbHelper().getCareportalEvents(start, end, true)
        val selection = BloodGlucoseElement.fromCareportalEvents(readings)
        if (selection.isNotEmpty())
            RxBus.send(EventTidepoolStatus("${selection.size} BGs selected for upload"))
        return selection

    }

    internal fun getBgReadings(start: Long, end: Long): List<SensorGlucoseElement> {
        val readings = MainApp.getDbHelper().getBgreadingsDataFromTime(start, end, true)
        val selection = SensorGlucoseElement.fromBgReadings(readings)
        if (selection.isNotEmpty())
            RxBus.send(EventTidepoolStatus("${selection.size} CGMs selected for upload"))
        return selection
    }

    private fun getBasals(start: Long, end: Long): List<BasalElement> {
        val tbrs = TreatmentsPlugin.getPlugin().temporaryBasalsFromHistory
        tbrs.merge()
        val selection = BasalElement.fromTemporaryBasals(tbrs, start, end) // TODO do not upload running TBR
        if (selection.isNotEmpty())
            RxBus.send(EventTidepoolStatus("${selection.size} TBRs selected for upload"))
        return selection
    }

    private fun getProfiles(start: Long, end: Long): List<ProfileElement> {
        val pss = MainApp.getDbHelper().getProfileSwitchEventsFromTime(start, end, true)
        val selection = LinkedList<ProfileElement>()
        for (ps in pss) {
            ProfileElement.newInstanceOrNull(ps)?.let { selection.add(it) }
        }
        if (selection.size > 0)
            RxBus.send(EventTidepoolStatus("${selection.size} ProfileSwitches selected for upload"))
        return selection
    }

}