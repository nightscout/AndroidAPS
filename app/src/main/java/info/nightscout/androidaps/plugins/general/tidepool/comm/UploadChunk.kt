package info.nightscout.androidaps.plugins.general.tidepool.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Intervals
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.tidepool.elements.*
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolStatus
import info.nightscout.androidaps.plugins.general.tidepool.utils.GsonInstance
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class UploadChunk @Inject constructor(
    private val injector: HasAndroidInjector,
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val aapsLogger: AAPSLogger,
    private val profileFunction: ProfileFunction,
    private val treatmentsPlugin: TreatmentsPlugin,
    private val activePlugin: ActivePluginProvider,
    private val repository: AppRepository,
    private val dateUtil: DateUtil
) {

    private val MAX_UPLOAD_SIZE = T.days(7).msecs() // don't change this

    fun getNext(session: Session?): String? {
        if (session == null)
            return null

        session.start = getLastEnd()
        session.end = Math.min(session.start + MAX_UPLOAD_SIZE, DateUtil.now())

        val result = get(session.start, session.end)
        if (result.length < 3) {
            aapsLogger.debug(LTag.TIDEPOOL, "No records in this time period, setting start to best end time")
            setLastEnd(Math.max(session.end, getOldestRecordTimeStamp()))
        }
        return result
    }

    operator fun get(start: Long, end: Long): String {

        aapsLogger.debug(LTag.TIDEPOOL, "Syncing data between: " + dateUtil.dateAndTimeString(start) + " -> " + dateUtil.dateAndTimeString(end))
        if (end <= start) {
            aapsLogger.debug(LTag.TIDEPOOL, "End is <= start: " + dateUtil.dateAndTimeString(start) + " " + dateUtil.dateAndTimeString(end))
            return ""
        }
        if (end - start > MAX_UPLOAD_SIZE) {
            aapsLogger.debug(LTag.TIDEPOOL, "More than max range - rejecting")
            return ""
        }

        val records = LinkedList<BaseElement>()

        if (sp.getBoolean(R.string.key_tidepool_upload_bolus, true))
            records.addAll(getTreatments(start, end))
        if (sp.getBoolean(R.string.key_tidepool_upload_bg, true))
            records.addAll(getBloodTests(start, end))
        if (sp.getBoolean(R.string.key_tidepool_upload_tbr, true))
            records.addAll(getBasals(start, end))
        if (sp.getBoolean(R.string.key_tidepool_upload_cgm, true))
            records.addAll(getBgReadings(start, end))
        if (sp.getBoolean(R.string.key_tidepool_upload_profile, true))
            records.addAll(getProfiles(start, end))

        return GsonInstance.defaultGsonInstance().toJson(records)
    }

    fun getLastEnd(): Long {
        val result = sp.getLong(R.string.key_tidepool_last_end, 0)
        return max(result, DateUtil.now() - T.months(2).msecs())
    }

    fun setLastEnd(time: Long) {
        if (time > getLastEnd()) {
            sp.putLong(R.string.key_tidepool_last_end, time)
            val friendlyEnd = dateUtil.dateAndTimeString(time)
            rxBus.send(EventTidepoolStatus(("Marking uploaded data up to $friendlyEnd")))
            aapsLogger.debug(LTag.TIDEPOOL, "Updating last end to: " + dateUtil.dateAndTimeString(time))
        } else {
            aapsLogger.debug(LTag.TIDEPOOL, "Cannot set last end to: " + dateUtil.dateAndTimeString(time) + " vs " + dateUtil.dateAndTimeString(getLastEnd()))
        }
    }

    // numeric limits must match max time windows

    private fun getOldestRecordTimeStamp(): Long {
        // TODO we could make sure we include records older than the first bg record for completeness

        val start: Long = 0
        val end = DateUtil.now()

        val bgReadingList = repository.compatGetBgReadingsDataFromTime(start, end, true)
            .blockingGet()
        return if (bgReadingList.isNotEmpty())
            bgReadingList[0].timestamp
        else -1
    }

    private fun getTreatments(start: Long, end: Long): List<BaseElement> {
        val result = LinkedList<BaseElement>()
        val treatments = treatmentsPlugin.service.getTreatmentDataFromTime(start, end, true)
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
            rxBus.send(EventTidepoolStatus("${selection.size} BGs selected for upload"))
        return selection

    }

    private fun getBgReadings(start: Long, end: Long): List<SensorGlucoseElement> {
        val readings = repository.compatGetBgReadingsDataFromTime(start, end, true)
            .blockingGet()
        val selection = SensorGlucoseElement.fromBgReadings(readings)
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} CGMs selected for upload"))
        return selection
    }

    private fun fromTemporaryBasals(tbrList: Intervals<TemporaryBasal>, start: Long, end: Long): List<BasalElement> {
        val results = LinkedList<BasalElement>()
        for (tbr in tbrList.list) {
            if (tbr.date >= start && tbr.date <= end && tbr.durationInMinutes != 0)
                results.add(BasalElement(tbr, profileFunction))
        }
        return results
    }

    private fun getBasals(start: Long, end: Long): List<BasalElement> {
        val tbrs = treatmentsPlugin.temporaryBasalsFromHistory
        tbrs.merge()
        val selection = fromTemporaryBasals(tbrs, start, end) // TODO do not upload running TBR
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} TBRs selected for upload"))
        return selection
    }

    fun newInstanceOrNull(ps: ProfileSwitch): ProfileElement? = try {
        ProfileElement(ps, activePlugin.activePump.serialNumber())
    } catch (e: Throwable) {
        null
    }

    private fun getProfiles(start: Long, end: Long): List<ProfileElement> {
        val pss = MainApp.getDbHelper().getProfileSwitchEventsFromTime(start, end, true)
        val selection = LinkedList<ProfileElement>()
        for (ps in pss) {
            newInstanceOrNull(ps)?.let { selection.add(it) }
        }
        if (selection.size > 0)
            rxBus.send(EventTidepoolStatus("${selection.size} ProfileSwitches selected for upload"))
        return selection
    }

}