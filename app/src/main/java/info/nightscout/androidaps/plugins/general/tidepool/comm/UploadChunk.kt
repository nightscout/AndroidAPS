package info.nightscout.androidaps.plugins.general.tidepool.comm

import android.util.Log
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.general.tidepool.elements.*
import info.nightscout.androidaps.plugins.general.tidepool.utils.GsonInstance
import info.nightscout.androidaps.plugins.general.tidepool.utils.LogSlider
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.T
import java.util.*

object UploadChunk {

    private val TAG = "TidepoolUploadChunk"

    private val MAX_UPLOAD_SIZE = T.days(7).msecs() // don't change this
    private val DEFAULT_WINDOW_OFFSET = T.mins(15).msecs()
    private val MAX_LATENCY_THRESHOLD_MINUTES: Long = 1440 // minutes per day


    fun getNext(session: Session): String? {
        session.start = getLastEnd()
        session.end = maxWindow(session.start)

        val result = get(session.start, session.end)
        if (result != null && result.length < 3) {
            Log.d(TAG, "No records in this time period, setting start to best end time")
            setLastEnd(Math.max(session.end, getOldestRecordTimeStamp()))
        }
        return result
    }

    operator fun get(start: Long, end: Long): String? {

        Log.e(TAG, "Syncing data between: " + DateUtil.dateAndTimeFullString(start) + " -> " + DateUtil.dateAndTimeFullString(end))
        if (end <= start) {
            Log.e(TAG, "End is <= start: " + DateUtil.dateAndTimeFullString(start) + " " + DateUtil.dateAndTimeFullString(end))
            return null
        }
        if (end - start > MAX_UPLOAD_SIZE) {
            Log.e(TAG, "More than max range - rejecting")
            return null
        }

        val records = LinkedList<BaseElement>()

        records.addAll(getTreatments(start, end))
        records.addAll(getBloodTests(start, end))
        records.addAll(getBasals(start, end))
        records.addAll(getBgReadings(start, end))

        return GsonInstance.defaultGsonInstance().toJson(records)
    }

    private fun getWindowSizePreference(): Long {
        try {
            val value = getLatencySliderValue(SP.getInt(R.string.key_tidepool_window_latency, 0)).toLong()
            return Math.max(T.mins(value).msecs(), DEFAULT_WINDOW_OFFSET)
        } catch (e: Exception) {
            Log.e(TAG, "Reverting to default of 15 minutes due to Window Size exception: $e")
            return DEFAULT_WINDOW_OFFSET // default
        }

    }

    private fun maxWindow(last_end: Long): Long {
        //Log.d(TAG, "Max window is: " + getWindowSizePreference());
        return Math.min(last_end + MAX_UPLOAD_SIZE, DateUtil.now() - getWindowSizePreference())
    }

    fun getLastEnd(): Long {
        val result = SP.getLong(R.string.key_tidepool_last_end, 0)
        return Math.max(result, DateUtil.now() - T.months(2).msecs())
    }

    fun setLastEnd(time: Long) {
        if (time > getLastEnd()) {
            SP.putLong(R.string.key_tidepool_last_end, time)
            Log.d(TAG, "Updating last end to: " + DateUtil.dateAndTimeFullString(time))
        } else {
            Log.e(TAG, "Cannot set last end to: " + DateUtil.dateAndTimeFullString(time) + " vs " + DateUtil.dateAndTimeFullString(getLastEnd()))
        }
    }

    internal fun getTreatments(start: Long, end: Long): List<BaseElement> {
        val result = LinkedList<BaseElement>()
        val treatments = TreatmentsPlugin.getPlugin().service.getTreatmentDataFromTime(start, end, true)
        for (treatment in treatments) {
            if (treatment.carbs > 0) {
                result.add(WizardElement.fromTreatment(treatment))
            } else if (treatment.insulin > 0) {
                result.add(BolusElement.fromTreatment(treatment))
            } else {
                // note only TODO
            }
        }
        return result
    }


    // numeric limits must match max time windows

    internal fun getOldestRecordTimeStamp(): Long {
        // TODO we could make sure we include records older than the first bg record for completeness

        val start: Long = 0
        val end = DateUtil.now()

        val bgReadingList = MainApp.getDbHelper().getBgreadingsDataFromTime(start, end, false)
        return if (bgReadingList != null && bgReadingList.size > 0) {
            bgReadingList[0].date
        } else -1
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun getBloodTests(start: Long, end: Long): List<BloodGlucoseElement> {
        return ArrayList()
        //        return BloodGlucoseElement.fromBloodTests(BloodTest.latestForGraph(1800, start, end));
    }

    internal fun getBgReadings(start: Long, end: Long): List<SensorGlucoseElement> {
        return SensorGlucoseElement.fromBgReadings(MainApp.getDbHelper().getBgreadingsDataFromTime(start, end, true))
    }

    internal fun getBasals(start: Long, end: Long): List<BasalElement> {
        val basals = LinkedList<BasalElement>()
        val aplist = MainApp.getDbHelper().getTemporaryBasalsDataFromTime(start, end, true)
        var current: BasalElement? = null
        for (temporaryBasal in aplist) {
            val this_rate = temporaryBasal.tempBasalConvertedToAbsolute(temporaryBasal.date, ProfileFunctions.getInstance().getProfile(temporaryBasal.date))

            if (current != null) {
                if (this_rate != current.rate) {
                    current.duration = temporaryBasal.date - current.timestamp
                    Log.d(TAG, "Adding current: " + current.toS())
                    if (current.isValid()) {
                        basals.add(current)
                    } else {
                        Log.e(TAG, "Current basal is invalid: " + current.toS())
                    }
                    current = null
                } else {
                    Log.d(TAG, "Same rate as previous basal record: " + current.rate + " " + temporaryBasal.toStringFull())
                }
            }
            if (current == null) {
                current = BasalElement().create(this_rate, temporaryBasal.date, 0, UUID.nameUUIDFromBytes(("tidepool-basal" + temporaryBasal.date).toByteArray()).toString()) // start duration is 0
            }
        }
        return basals

    }

    fun interpolate(name: String, position: Int): Int {
        when (name) {
            "latency" -> return getLatencySliderValue(position)
        }
        throw RuntimeException("name not matched in interpolate")
    }

    private fun getLatencySliderValue(position: Int): Int {
        return LogSlider.calc(0, 300, 15.0, MAX_LATENCY_THRESHOLD_MINUTES.toDouble(), position).toInt()
    }

}