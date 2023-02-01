package info.nightscout.plugins.sync.tidepool.comm

import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.tidepool.elements.BasalElement
import info.nightscout.plugins.sync.tidepool.elements.BaseElement
import info.nightscout.plugins.sync.tidepool.elements.BloodGlucoseElement
import info.nightscout.plugins.sync.tidepool.elements.BolusElement
import info.nightscout.plugins.sync.tidepool.elements.ProfileElement
import info.nightscout.plugins.sync.tidepool.elements.SensorGlucoseElement
import info.nightscout.plugins.sync.tidepool.elements.WizardElement
import info.nightscout.plugins.sync.tidepool.events.EventTidepoolStatus
import info.nightscout.plugins.sync.tidepool.utils.GsonInstance
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class UploadChunk @Inject constructor(
    private val sp: SP,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val repository: AppRepository,
    private val dateUtil: DateUtil
) {

    private val maxUploadSize = T.days(7).msecs() // don't change this

    fun getNext(session: Session?): String? {
        session ?: return null

        session.start = getLastEnd()
        // do not upload last 3h, TBR can be still running
        session.end = min(session.start + maxUploadSize, dateUtil.now() - T.hours(3).msecs())

        val result = get(session.start, session.end)
        if (result.length < 3) {
            aapsLogger.debug(LTag.TIDEPOOL, "No records in this time period, setting start to best end time")
            setLastEnd(session.end)
        }
        return result
    }

    fun get(start: Long, end: Long): String {

        aapsLogger.debug(LTag.TIDEPOOL, "Syncing data between: " + dateUtil.dateAndTimeString(start) + " -> " + dateUtil.dateAndTimeString(end))
        if (end <= start) {
            aapsLogger.debug(LTag.TIDEPOOL, "End is <= start: " + dateUtil.dateAndTimeString(start) + " " + dateUtil.dateAndTimeString(end))
            return ""
        }
        if (end - start > maxUploadSize) {
            aapsLogger.debug(LTag.TIDEPOOL, "More than max range - rejecting")
            return ""
        }

        val records = LinkedList<BaseElement>()

        records.addAll(getTreatments(start, end))
        records.addAll(getBloodTests(start, end))
        records.addAll(getBasals(start, end))
        records.addAll(getBgReadings(start, end))
        records.addAll(getProfiles(start, end))

        return GsonInstance.defaultGsonInstance().toJson(records)
    }

    fun getLastEnd(): Long {
        val result = sp.getLong(R.string.key_tidepool_last_end, 0)
        return max(result, dateUtil.now() - T.months(2).msecs())
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

    private fun getTreatments(start: Long, end: Long): List<BaseElement> {
        val result = LinkedList<BaseElement>()
        repository.getBolusesDataFromTimeToTime(start, end, true)
            .blockingGet()
            .forEach { bolus ->
                result.add(BolusElement(bolus, dateUtil))
            }
        repository.getCarbsDataFromTimeToTimeExpanded(start, end, true)
            .blockingGet()
            .forEach { carb ->
                result.add(WizardElement(carb, dateUtil))
            }
        return result
    }

    private fun getBloodTests(start: Long, end: Long): List<BloodGlucoseElement> {
        val readings = repository.compatGetTherapyEventDataFromToTime(start, end).blockingGet()
        val selection = BloodGlucoseElement.fromCareportalEvents(readings, dateUtil)
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} BGs selected for upload"))
        return selection

    }

    private fun getBgReadings(start: Long, end: Long): List<SensorGlucoseElement> {
        val readings = repository.compatGetBgReadingsDataFromTime(start, end, true)
            .blockingGet()
        val selection = SensorGlucoseElement.fromBgReadings(readings, dateUtil)
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} CGMs selected for upload"))
        return selection
    }

    private fun fromTemporaryBasals(tbrList: List<TemporaryBasal>, start: Long, end: Long): List<BasalElement> {
        val results = LinkedList<BasalElement>()
        for (tbr in tbrList) {
            if (tbr.timestamp in start..end)
                profileFunction.getProfile(tbr.timestamp)?.let {
                    results.add(BasalElement(tbr, it, dateUtil))
                }
        }
        return results
    }

    private fun getBasals(start: Long, end: Long): List<BasalElement> {
        val temporaryBasals = repository.getTemporaryBasalsDataFromTimeToTime(start, end, true).blockingGet()
        val selection = fromTemporaryBasals(temporaryBasals, start, end) // TODO do not upload running TBR
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} TBRs selected for upload"))
        return selection
    }

    private fun newInstanceOrNull(ps: EffectiveProfileSwitch): ProfileElement? = try {
        ProfileElement(ps, activePlugin.activePump.serialNumber(), dateUtil)
    } catch (e: Throwable) {
        null
    }

    private fun getProfiles(start: Long, end: Long): List<ProfileElement> {
        val pss = repository.getEffectiveProfileSwitchDataFromTimeToTime(start, end, true).blockingGet()
        val selection = LinkedList<ProfileElement>()
        for (ps in pss) {
            newInstanceOrNull(ps)?.let {
                selection.add(it)
            }
        }
        if (selection.size > 0)
            rxBus.send(EventTidepoolStatus("${selection.size} ProfileSwitches selected for upload"))
        return selection
    }

}