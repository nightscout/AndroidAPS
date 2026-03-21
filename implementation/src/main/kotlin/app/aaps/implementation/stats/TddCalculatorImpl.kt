package app.aaps.implementation.stats

import androidx.collection.LongSparseArray
import app.aaps.core.data.aps.AverageTDD
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TDD
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Implementation of Total Daily Dose (TDD) calculator for insulin usage statistics.
 *
 * This class calculates comprehensive insulin usage statistics including basal insulin,
 * bolus insulin (including extended boluses), and carbohydrate intake over specified
 * time periods. It supports caching of calculated values for performance optimization.
 *
 * Key features:
 * - Calculates TDD for multiple days (typically 7 or 30 days)
 * - Supports partial day calculations (e.g., "today so far")
 * - Caches calculated daily totals in the database
 * - Handles daylight saving time transitions correctly
 * - Accounts for temporary basal rates (TBR)
 * - Includes extended boluses (if not faked by pump)
 *
 * The calculator operates in 5-minute intervals to accurately capture basal rates
 * and temporary basal adjustments. It retrieves boluses and carbs directly from
 * the database, while calculating actual basal delivery from profiles and TBR data.
 *
 * Calculation methodology:
 * 1. Retrieve cached TDD values for requested days
 * 2. Calculate missing days using 5-minute interval sampling
 * 3. For each interval: get active profile, TBR, and extended bolus
 * 4. Sum all insulin delivery and carbs
 * 5. Cache results marked as PumpType.CACHE
 *
 * Missing data handling:
 * - If allowMissingDays=false and any day cannot be calculated, returns null
 * - If allowMissingDays=true, returns partial results
 * - Missing profile data causes day calculation to fail (unless allowMissingData=true)
 *
 * @property aapsLogger Logger for debug output
 * @property activePlugin Access to active pump for extended bolus handling
 * @property profileFunction Access to insulin profiles for basal rate calculation
 * @property dateUtil Date/time utilities
 * @property iobCobCalculator Calculator for basal data including TBR
 * @property persistenceLayer Database access for boluses, carbs, and cached TDD
 *
 * @see TddCalculator
 * @see app.aaps.core.data.model.TDD
 * @see app.aaps.core.data.aps.AverageTDD
 */
@Reusable
class TddCalculatorImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val iobCobCalculator: IobCobCalculator,
    private val persistenceLayer: PersistenceLayer,
    @ApplicationScope private val appScope: CoroutineScope
) : TddCalculator {

    override fun calculate(days: Long, allowMissingDays: Boolean): LongSparseArray<TDD>? =
        calculate(dateUtil.now(), days, allowMissingDays)

    override fun calculate(timestamp: Long, days: Long, allowMissingDays: Boolean): LongSparseArray<TDD>? {
        var startTime = MidnightTime.calcDaysBack(timestamp, days)
        val endTime = Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault())
            .plusDays(days)
            .toInstant().toEpochMilli()

        aapsLogger.debug(LTag.APS, "Calculating TotalDailyDose from ${dateUtil.dateString(startTime)} to ${dateUtil.dateString(endTime)}")

        val result = LongSparseArray<TDD>()
        // Try to load cached values
        while (startTime < endTime) {
            aapsLogger.debug(LTag.APS, "Looking for cached TotalDailyDose for ${dateUtil.dateString(startTime)}")
            runBlocking { persistenceLayer.getCalculatedTotalDailyDose(startTime) }?.let {
                result.put(startTime, it)
                aapsLogger.debug(LTag.APS, "Loaded cached TotalDailyDose for ${dateUtil.dateString(it.timestamp)} $it")
            } ?: break
            startTime = MidnightTime.calc(startTime + T.hours(27).msecs()) // be sure we find correct midnight during DST change
        }

        if (endTime > startTime) {
            var midnight = startTime
            while (midnight < endTime) {
                val tdd = calculateInterval(midnight, midnight + T.hours(24).msecs(), allowMissingData = false)
                aapsLogger.debug(LTag.APS, "Calculated TotalDailyDose for ${dateUtil.dateString(midnight)} $tdd")
                if (tdd != null) result.put(midnight, tdd)
                midnight = MidnightTime.calc(midnight + T.hours(27).msecs()) // be sure we find correct midnight
            }
        }
        for (i in 0 until result.size()) {
            val tdd = result.valueAt(i)
            if (tdd.ids.pumpType != PumpType.CACHE) {
                tdd.ids.pumpType = PumpType.CACHE
                appScope.launch { persistenceLayer.insertOrUpdateCachedTotalDailyDose(tdd) }
            } else {
                aapsLogger.debug(LTag.APS, "Skipping storing TotalDailyDose for ${dateUtil.dateString(tdd.timestamp)}")
            }
        }
        if (result.size().toLong() == days || allowMissingDays) return result
        return null
    }

    override fun calculateToday(): TDD? {
        val startTime = MidnightTime.calc(dateUtil.now())
        val endTime = dateUtil.now()
        return calculateInterval(startTime, endTime, allowMissingData = true)
    }

    override fun calculateDaily(startHours: Long, endHours: Long): TDD? =
        calculateDaily(dateUtil.now(), startHours, endHours)

    override fun calculateDaily(timestamp: Long, startHours: Long, endHours: Long): TDD? {
        assert(startHours < 0)
        assert(endHours <= 0)
        val startTime = timestamp + T.hours(hour = startHours).msecs()
        val endTime = timestamp + T.hours(hour = endHours).msecs()
        return calculateInterval(startTime, endTime, allowMissingData = false)
    }

    override fun calculateInterval(startTime: Long, endTime: Long, allowMissingData: Boolean): TDD? {
        val startTimeAligned = startTime - startTime % (5 * 60 * 1000)
        val endTimeAligned = endTime - endTime % (5 * 60 * 1000)
        val tdd = TDD(timestamp = startTimeAligned)
        var tbrFound = false
        runBlocking { persistenceLayer.getBolusesFromTimeToTime(startTime, endTime, true) }
            .filter { it.type != BS.Type.PRIMING }
            .forEach { t ->
                tdd.bolusAmount += t.amount
            }
        runBlocking { persistenceLayer.getCarbsFromTimeToTimeExpanded(startTime, endTime, true) }.forEach { t ->
            tdd.carbs += t.amount
            val profile = runBlocking { profileFunction.getProfile(t.timestamp) }
            if (profile != null) {
                val ic = profile.getIc(t.timestamp)
                if (ic > 0) tdd.carbInsulin += t.amount / ic
            }
        }
        val calculationStep = T.mins(5).msecs()
        for (t in startTimeAligned until endTimeAligned step calculationStep) {

            val profile = runBlocking { profileFunction.getProfile(t) } ?: if (allowMissingData) continue else return null
            val tbr = iobCobCalculator.getBasalData(profile, t)
            if (tbr.isTempBasalRunning) tbrFound = true
            val absoluteRate = tbr.tempBasalAbsolute
            tdd.basalAmount += absoluteRate / 60.0 * 5.0

            if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) {
                val eb = runBlocking { persistenceLayer.getExtendedBolusActiveAt(t) }
                val absoluteEbRate = eb?.rate ?: 0.0
                tdd.bolusAmount += absoluteEbRate / 60.0 * 5.0
            }
        }
        tdd.totalAmount = tdd.bolusAmount + tdd.basalAmount
        //aapsLogger.debug(LTag.CORE, tdd.toString())
        if (tdd.bolusAmount > 0 || tdd.basalAmount > 0 || tbrFound) return tdd
        return null
    }

    override fun averageTDD(tdds: LongSparseArray<TDD>?): AverageTDD? {
        val totalTdd = TDD(timestamp = dateUtil.now())
        tdds ?: return null
        if (tdds.size() == 0) return null
        var hasCarbs = true
        for (i in 0 until tdds.size()) {
            val tdd = tdds.valueAt(i)
            totalTdd.basalAmount += tdd.basalAmount
            totalTdd.bolusAmount += tdd.bolusAmount
            totalTdd.totalAmount += tdd.totalAmount
            totalTdd.carbs += tdd.carbs
            totalTdd.carbInsulin += tdd.carbInsulin
            if (tdd.carbs == 0.0) hasCarbs = false
        }
        totalTdd.basalAmount /= tdds.size().toDouble()
        totalTdd.bolusAmount /= tdds.size().toDouble()
        totalTdd.totalAmount /= tdds.size().toDouble()
        totalTdd.carbs /= tdds.size().toDouble()
        totalTdd.carbInsulin /= tdds.size().toDouble()
        return AverageTDD(data = totalTdd, allDaysHaveCarbs = hasCarbs)
    }
}
