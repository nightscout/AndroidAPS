package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.tidepool.elements.BasalElement
import app.aaps.plugins.sync.tidepool.elements.BaseElement
import app.aaps.plugins.sync.tidepool.elements.BloodGlucoseElement
import app.aaps.plugins.sync.tidepool.elements.BolusElement
import app.aaps.plugins.sync.tidepool.elements.ProfileElement
import app.aaps.plugins.sync.tidepool.elements.SensorGlucoseElement
import app.aaps.plugins.sync.tidepool.elements.WizardElement
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.keys.TidepoolLongNonKey
import app.aaps.plugins.sync.tidepool.utils.GsonInstance
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class UploadChunk @Inject constructor(
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
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
        val result = preferences.get(TidepoolLongNonKey.LastEnd)
        return max(result, dateUtil.now() - T.months(2).msecs())
    }

    fun setLastEnd(time: Long) {
        if (time > getLastEnd()) {
            preferences.put(TidepoolLongNonKey.LastEnd, time)
            val friendlyEnd = dateUtil.dateAndTimeString(time)
            rxBus.send(EventTidepoolStatus(("Marking uploaded data up to $friendlyEnd")))
            aapsLogger.debug(LTag.TIDEPOOL, "Updating last end to: " + dateUtil.dateAndTimeString(time))
        } else {
            aapsLogger.debug(LTag.TIDEPOOL, "Cannot set last end to: " + dateUtil.dateAndTimeString(time) + " vs " + dateUtil.dateAndTimeString(getLastEnd()))
        }
    }

    private fun getTreatments(start: Long, end: Long): List<BaseElement> {
        val result = LinkedList<BaseElement>()
        persistenceLayer.getBolusesFromTimeToTime(start, end, true)
            .forEach { bolus ->
                result.add(BolusElement(bolus, dateUtil))
            }
        persistenceLayer.getCarbsFromTimeToTimeExpanded(start, end, true)
            .forEach { carb ->
                if (carb.amount > 0.0)
                    result.add(WizardElement(carb, dateUtil))
            }
        return result
    }

    private fun getBloodTests(start: Long, end: Long): List<BloodGlucoseElement> {
        val readings = persistenceLayer.getTherapyEventDataFromToTime(start, end).blockingGet()
        val selection = BloodGlucoseElement.fromCareportalEvents(readings, dateUtil, profileUtil)
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} BGs selected for upload"))
        return selection

    }

    private fun getBgReadings(start: Long, end: Long): List<SensorGlucoseElement> {
        val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true)
        val selection = SensorGlucoseElement.fromBgReadings(readings, dateUtil)
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} CGMs selected for upload"))
        return selection
    }

    private fun fromTemporaryBasals(tbrList: List<TB>, start: Long, end: Long): List<BasalElement> {
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
        val temporaryBasals = persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(start, end, true)
        val selection = fromTemporaryBasals(temporaryBasals, start, end)
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} TBRs selected for upload"))
        return selection
    }

    private fun newInstanceOrNull(ps: EPS): ProfileElement? = try {
        ProfileElement(ps, activePlugin.activePump.serialNumber(), dateUtil, profileUtil)
    } catch (_: Throwable) {
        null
    }

    private fun getProfiles(start: Long, end: Long): List<ProfileElement> {
        val pss = persistenceLayer.getEffectiveProfileSwitchesFromTimeToTime(start, end, true)
        val selection = LinkedList<ProfileElement>()
        for (ps in pss) {
            newInstanceOrNull(ps)?.let {
                selection.add(it)
            }
        }
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} ProfileSwitches selected for upload"))
        return selection
    }

}