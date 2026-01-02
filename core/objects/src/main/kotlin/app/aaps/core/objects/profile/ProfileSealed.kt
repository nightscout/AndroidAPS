package app.aaps.core.objects.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.Profile.ProfileValue
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.objects.extensions.blockValueBySeconds
import app.aaps.core.objects.extensions.highTargetBlockValueBySeconds
import app.aaps.core.objects.extensions.lowTargetBlockValueBySeconds
import app.aaps.core.objects.extensions.shiftBlock
import app.aaps.core.objects.extensions.shiftTargetBlock
import app.aaps.core.objects.extensions.targetBlockValueBySeconds
import app.aaps.core.ui.R
import app.aaps.core.utils.MidnightUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.TimeZone

sealed class ProfileSealed(
    val id: Long,
    val isValid: Boolean,
    val ids: IDs?,
    val timestamp: Long,
    var basalBlocks: List<Block>,
    var isfBlocks: List<Block>,
    var icBlocks: List<Block>,
    var targetBlocks: List<TargetBlock>,
    val profileName: String,
    var duration: Long?, // [milliseconds]
    var ts: Int, // timeshift [hours]
    var pct: Int,
    var iCfg: ICfg,
    val utcOffset: Long,
    val aps: APS?
) : Profile {

    /**
     * Profile interface created from ProfileSwitch
     * @param value ProfileSwitch
     * @param activePlugin access to active APS. Must be provided only when accessing dynamic runtime IC, ISF
     */
    data class PS(val value: app.aaps.core.data.model.PS, val activePlugin: ActivePlugin?) : ProfileSealed(
        value.id,
        value.isValid,
        value.ids,
        value.timestamp,
        value.basalBlocks,
        value.isfBlocks,
        value.icBlocks,
        value.targetBlocks,
        value.profileName,
        value.duration,
        T.msecs(value.timeshift).hours().toInt(),
        value.percentage,
        value.iCfg,
        value.utcOffset,
        activePlugin?.activeAPS
    )

    /**
     * Profile interface created from EffectiveProfileSwitch
     * @param value EffectiveProfileSwitch
     * @param activePlugin access to active APS. Must be provided only when accessing dynamic runtime IC, ISF
     */
    data class EPS(val value: app.aaps.core.data.model.EPS, val activePlugin: ActivePlugin?) : ProfileSealed(
        value.id,
        value.isValid,
        value.ids,
        value.timestamp,
        value.basalBlocks,
        value.isfBlocks,
        value.icBlocks,
        value.targetBlocks,
        value.originalProfileName,
        null, // already converted to non customized
        0, // already converted to non customized
        100, // already converted to non customized
        value.iCfg,
        value.utcOffset,
        activePlugin?.activeAPS
    )

    /**
     * Profile interface created from PureProfile ie. without customization
     * @param value PureProfile
     * @param activePlugin access to active APS. Must be provided only when accessing dynamic runtime IC, ISF
     */
    data class Pure(val value: PureProfile, val activePlugin: ActivePlugin?) : ProfileSealed(
        0,
        true,
        null,
        0,
        value.basalBlocks,
        value.isfBlocks,
        value.icBlocks,
        value.targetBlocks,
        "",
        null,
        0,
        100,
        ICfg("", (value.dia * 3600 * 1000).toLong(), 0),
        value.timeZone.rawOffset.toLong(),
        activePlugin?.activeAPS
    )

    override fun isValid(from: String, pump: Pump, config: Config, rh: ResourceHelper, rxBus: RxBus, hardLimits: HardLimits, sendNotifications: Boolean): Profile.ValidityCheck {
        val validityCheck = Profile.ValidityCheck()
        val description = pump.pumpDescription

        for (basal in basalBlocks) {
            val basalAmount = basal.amount * percentage / 100.0
            if (!description.is30minBasalRatesCapable) {
                // Check for hours alignment
                val duration: Long = basal.duration
                if (duration % 3600000 != 0L) {
                    if (sendNotifications && config.APS) {
                        val notification = Notification(
                            Notification.BASAL_PROFILE_NOT_ALIGNED_TO_HOURS,
                            rh.gs(R.string.basalprofilenotaligned, from),
                            Notification.NORMAL
                        )
                        rxBus.send(EventNewNotification(notification))
                    }
                    validityCheck.isValid = false
                    validityCheck.reasons.add(
                        rh.gs(R.string.basalprofilenotaligned, from)
                    )
                    break
                }
            }
            if (!hardLimits.isInRange(basalAmount, 0.01, hardLimits.maxBasal())) {
                validityCheck.isValid = false
                validityCheck.reasons.add(rh.gs(R.string.value_out_of_hard_limits, rh.gs(R.string.basal_value), basalAmount))
                break
            }
            // Check for minimal basal value
            if (basalAmount < description.basalMinimumRate) {
                basal.amount = description.basalMinimumRate
                if (sendNotifications) sendBelowMinimumNotification(from, rxBus, rh)
                validityCheck.isValid = false
                validityCheck.reasons.add(rh.gs(R.string.minimalbasalvaluereplaced, from))
                break
            } else if (basalAmount > description.basalMaximumRate) {
                basal.amount = description.basalMaximumRate
                if (sendNotifications) sendAboveMaximumNotification(from, rxBus, rh)
                validityCheck.isValid = false
                validityCheck.reasons.add(rh.gs(R.string.maximumbasalvaluereplaced, from))
                break
            }
        }

        if (!hardLimits.isInRange(dia, hardLimits.minDia(), hardLimits.maxDia())) {
            validityCheck.isValid = false
            validityCheck.reasons.add(rh.gs(R.string.value_out_of_hard_limits, rh.gs(R.string.profile_dia), dia))
        }
        for (ic in icBlocks)
            if (!hardLimits.isInRange(ic.amount * 100.0 / percentage, hardLimits.minIC(), hardLimits.maxIC())) {
                validityCheck.isValid = false
                validityCheck.reasons.add(
                    rh.gs(
                        R.string.value_out_of_hard_limits,
                        rh.gs(R.string.profile_carbs_ratio_value),
                        ic.amount * 100.0 / percentage
                    )
                )
                break
            }
        for (isf in isfBlocks)
            if (!hardLimits.isInRange(toMgdl(isf.amount * 100.0 / percentage, units), HardLimits.MIN_ISF, HardLimits.MAX_ISF)) {
                validityCheck.isValid = false
                validityCheck.reasons.add(
                    rh.gs(
                        R.string.value_out_of_hard_limits,
                        rh.gs(R.string.profile_sensitivity_value),
                        isf.amount * 100.0 / percentage
                    )
                )
                break
            }
        for (target in targetBlocks) {
            if (!hardLimits.isInRange(
                    toMgdl(target.lowTarget, units),
                    HardLimits.LIMIT_MIN_BG[0],
                    HardLimits.LIMIT_MIN_BG[1]
                )
            ) {
                validityCheck.isValid = false
                validityCheck.reasons.add(rh.gs(R.string.value_out_of_hard_limits, rh.gs(R.string.profile_low_target), target.lowTarget))
                break
            }
            if (!hardLimits.isInRange(
                    toMgdl(target.highTarget, units),
                    HardLimits.LIMIT_MAX_BG[0],
                    HardLimits.LIMIT_MAX_BG[1]
                )
            ) {
                validityCheck.isValid = false
                validityCheck.reasons.add(rh.gs(R.string.value_out_of_hard_limits, rh.gs(R.string.profile_high_target), target.highTarget))
                break
            }
        }
        return validityCheck
    }

    protected open fun sendBelowMinimumNotification(from: String, rxBus: RxBus, rh: ResourceHelper) {
        rxBus.send(EventNewNotification(Notification(Notification.MINIMAL_BASAL_VALUE_REPLACED, rh.gs(R.string.minimalbasalvaluereplaced, from), Notification.NORMAL)))
    }

    protected open fun sendAboveMaximumNotification(from: String, rxBus: RxBus, rh: ResourceHelper) {
        rxBus.send(EventNewNotification(Notification(Notification.MAXIMUM_BASAL_VALUE_REPLACED, rh.gs(R.string.maximumbasalvaluereplaced, from), Notification.NORMAL)))
    }

    override val units: GlucoseUnit
        get() = when (this) {
            is PS   -> value.glucoseUnit
            is EPS  -> value.glucoseUnit
            is Pure -> value.glucoseUnit
        }
    override val dia: Double
        get() = iCfg.insulinEndTime / 1000.0 / 60.0 / 60.0

    override val timeshift: Int
        get() = ts

    override fun isEqual(profile: Profile): Boolean {
        for (hour in 0..23) {
            val seconds = T.hours(hour.toLong()).secs().toInt()
            if (getBasalTimeFromMidnight(seconds) != profile.getBasalTimeFromMidnight(seconds)) return false
            if (getIsfMgdlTimeFromMidnight(seconds) != profile.getIsfMgdlTimeFromMidnight(seconds)) return false
            if (getIcTimeFromMidnight(seconds) != profile.getIcTimeFromMidnight(seconds)) return false
            if (getTargetLowMgdlTimeFromMidnight(seconds) != profile.getTargetLowMgdlTimeFromMidnight(seconds)) return false
            if (getTargetHighMgdlTimeFromMidnight(seconds) != profile.getTargetHighMgdlTimeFromMidnight(seconds)) return false
        }
        if (dia != profile.dia) return false
        return !((profile is EPS) && profileName != profile.value.originalProfileName) // handle profile name change too
    }

    override val percentage: Int
        get() = pct

    override fun getBasal(): Double = basalBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(), percentage / 100.0, timeshift)
    override fun getBasal(timestamp: Long): Double = basalBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(timestamp), percentage / 100.0, timeshift)
    override fun getIc(): Double =
        if (aps?.supportsDynamicIc() ?: error("APS not defined"))
            aps.getIc(this) ?: icBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(), 100.0 / percentage, timeshift)
        else icBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(), 100.0 / percentage, timeshift)

    override fun getIc(timestamp: Long): Double =
        if (aps?.supportsDynamicIc() ?: error("APS not defined"))
            aps.getIc(timestamp, this) ?: icBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(timestamp), 100.0 / percentage, timeshift)
        else icBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(timestamp), 100.0 / percentage, timeshift)

    override fun getProfileIsfMgdl(): Double =
        toMgdl(isfBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(), 100.0 / percentage, timeshift), units)

    override fun getIsfMgdl(caller: String): Double =
        if (aps?.supportsDynamicIsf() ?: error("APS not defined"))
            aps.getIsfMgdl(this, caller) ?: toMgdl(isfBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(), 100.0 / percentage, timeshift), units)
        else getProfileIsfMgdl()

    override fun getIsfMgdlForCarbs(timestamp: Long, caller: String, config: Config, processedDeviceStatusData: ProcessedDeviceStatusData): Double =
        if (config.AAPSCLIENT) {
            processedDeviceStatusData.getAPSResult()?.isfMgdlForCarbs ?: toMgdl(isfBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(timestamp), 100.0 / percentage, timeshift), units)
        } else {
            if (aps?.supportsDynamicIsf() ?: error("APS not defined"))
                aps.getAverageIsfMgdl(timestamp, caller) ?: toMgdl(isfBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(timestamp), 100.0 / percentage, timeshift), units)
            else toMgdl(isfBlocks.blockValueBySeconds(MidnightUtils.secondsFromMidnight(timestamp), 100.0 / percentage, timeshift), units)
        }


    override fun getTargetMgdl(): Double = toMgdl(targetBlocks.targetBlockValueBySeconds(MidnightUtils.secondsFromMidnight(), timeshift), units)
    override fun getTargetLowMgdl(): Double = toMgdl(targetBlocks.lowTargetBlockValueBySeconds(MidnightUtils.secondsFromMidnight(), timeshift), units)
    override fun getTargetLowMgdl(timestamp: Long): Double = toMgdl(targetBlocks.lowTargetBlockValueBySeconds(MidnightUtils.secondsFromMidnight(timestamp), timeshift), units)
    override fun getTargetHighMgdl(): Double = toMgdl(targetBlocks.highTargetBlockValueBySeconds(MidnightUtils.secondsFromMidnight(), timeshift), units)
    override fun getTargetHighMgdl(timestamp: Long): Double = toMgdl(targetBlocks.highTargetBlockValueBySeconds(MidnightUtils.secondsFromMidnight(timestamp), timeshift), units)
    override fun getBasalTimeFromMidnight(timeAsSeconds: Int): Double = basalBlocks.blockValueBySeconds(timeAsSeconds, percentage / 100.0, timeshift)
    override fun getIcTimeFromMidnight(timeAsSeconds: Int): Double = icBlocks.blockValueBySeconds(timeAsSeconds, 100.0 / percentage, timeshift)
    fun getIsfTimeFromMidnight(timeAsSeconds: Int): Double = isfBlocks.blockValueBySeconds(timeAsSeconds, 100.0 / percentage, timeshift)
    override fun getIsfMgdlTimeFromMidnight(timeAsSeconds: Int): Double = toMgdl(isfBlocks.blockValueBySeconds(timeAsSeconds, 100.0 / percentage, timeshift), units)
    override fun getTargetLowMgdlTimeFromMidnight(timeAsSeconds: Int): Double = toMgdl(targetBlocks.lowTargetBlockValueBySeconds(timeAsSeconds, timeshift), units)
    private fun getTargetLowTimeFromMidnight(timeAsSeconds: Int): Double = targetBlocks.lowTargetBlockValueBySeconds(timeAsSeconds, timeshift)
    private fun getTargetHighTimeFromMidnight(timeAsSeconds: Int): Double = targetBlocks.highTargetBlockValueBySeconds(timeAsSeconds, timeshift)
    override fun getTargetHighMgdlTimeFromMidnight(timeAsSeconds: Int): Double = toMgdl(targetBlocks.highTargetBlockValueBySeconds(timeAsSeconds, timeshift), units)

    override fun getIcList(rh: ResourceHelper, dateUtil: DateUtil): String =
        getValuesList(icBlocks, 100.0 / percentage, DecimalFormat("0.0"), rh.gs(R.string.profile_carbs_per_unit), dateUtil)

    override fun getIsfList(rh: ResourceHelper, dateUtil: DateUtil): String =
        getValuesList(isfBlocks, 100.0 / percentage, DecimalFormat("0.0"), units.asText + rh.gs(R.string.profile_per_unit), dateUtil)

    override fun getBasalList(rh: ResourceHelper, dateUtil: DateUtil): String =
        getValuesList(basalBlocks, percentage / 100.0, DecimalFormat("0.00"), rh.gs(R.string.profile_ins_units_per_hour), dateUtil)

    override fun getTargetList(rh: ResourceHelper, dateUtil: DateUtil): String = getTargetValuesList(targetBlocks, DecimalFormat("0.0"), units.asText, dateUtil)

    override fun convertToNonCustomizedProfile(dateUtil: DateUtil): PureProfile =
        PureProfile(
            jsonObject = toPureNsJson(dateUtil),
            basalBlocks = basalBlocks.shiftBlock(percentage / 100.0, timeshift),
            isfBlocks = isfBlocks.shiftBlock(100.0 / percentage, timeshift),
            icBlocks = icBlocks.shiftBlock(100.0 / percentage, timeshift),
            targetBlocks = targetBlocks.shiftTargetBlock(timeshift),
            glucoseUnit = units,
            dia = when (this) {
                is PS   -> this.value.iCfg.insulinEndTime / 3600.0 / 1000.0
                is EPS  -> this.value.iCfg.insulinEndTime / 3600.0 / 1000.0
                is Pure -> this.value.dia
            },
            timeZone = TimeZone.getDefault()
        )

    override fun toPureNsJson(dateUtil: DateUtil): JSONObject {
        val o = JSONObject()
        o.put("units", units.asText)
        o.put("dia", dia)
        o.put("timezone", dateUtil.timeZoneByOffset(utcOffset))
        // SENS
        val sens = JSONArray()
        var elapsedHours = 0L
        isfBlocks.forEach {
            sens.put(
                JSONObject()
                    .put("time", DecimalFormat("00").format(elapsedHours) + ":00")
                    .put("timeAsSeconds", T.hours(elapsedHours).secs())
                    .put("value", getIsfTimeFromMidnight(T.hours(elapsedHours).secs().toInt()))
            )
            elapsedHours += T.msecs(it.duration).hours()
        }
        o.put("sens", sens)
        val carbratio = JSONArray()
        elapsedHours = 0L
        icBlocks.forEach {
            carbratio.put(
                JSONObject()
                    .put("time", DecimalFormat("00").format(elapsedHours) + ":00")
                    .put("timeAsSeconds", T.hours(elapsedHours).secs())
                    .put("value", getIcTimeFromMidnight(T.hours(elapsedHours).secs().toInt()))
            )
            elapsedHours += T.msecs(it.duration).hours()
        }
        o.put("carbratio", carbratio)
        val basal = JSONArray()
        elapsedHours = 0L
        basalBlocks.forEach {
            basal.put(
                JSONObject()
                    .put("time", DecimalFormat("00").format(elapsedHours) + ":00")
                    .put("timeAsSeconds", T.hours(elapsedHours).secs())
                    .put("value", getBasalTimeFromMidnight(T.hours(elapsedHours).secs().toInt()))
            )
            elapsedHours += T.msecs(it.duration).hours()
        }
        o.put("basal", basal)
        val targetLow = JSONArray()
        val targetHigh = JSONArray()
        elapsedHours = 0L
        targetBlocks.forEach {
            targetLow.put(
                JSONObject()
                    .put("time", DecimalFormat("00").format(elapsedHours) + ":00")
                    .put("timeAsSeconds", T.hours(elapsedHours).secs())
                    .put("value", getTargetLowTimeFromMidnight(T.hours(elapsedHours).secs().toInt()))
            )
            targetHigh.put(
                JSONObject()
                    .put("time", DecimalFormat("00").format(elapsedHours) + ":00")
                    .put("timeAsSeconds", T.hours(elapsedHours).secs())
                    .put("value", getTargetHighTimeFromMidnight(T.hours(elapsedHours).secs().toInt()))
            )
            elapsedHours += T.msecs(it.duration).hours()
        }
        o.put("target_low", targetLow)
        o.put("target_high", targetHigh)
        return o
    }

    override fun getMaxDailyBasal(): Double = basalBlocks.maxByOrNull { it.amount }?.amount ?: 0.0

    override fun baseBasalSum(): Double {
        var result = 0.0
        for (i in 0..23) result += getBasalTimeFromMidnight(i * 60 * 60) / (percentage / 100.0) // it's recalculated. we need to recalculate back
        return result
    }

    override fun percentageBasalSum(): Double {
        var result = 0.0
        for (i in 0..23) result += getBasalTimeFromMidnight(i * 60 * 60)
        return result
    }

    override fun getBasalValues(): Array<ProfileValue> = getValues(basalBlocks, percentage / 100.0)
    override fun getIcsValues(): Array<ProfileValue> = getValues(icBlocks, 100.0 / percentage)

    override fun getIsfsMgdlValues(): Array<ProfileValue> {
        val shifted = isfBlocks.shiftBlock(100.0 / percentage, timeshift)
        val ret = Array(shifted.size) { ProfileValue(0, 0.0) }
        var elapsed = 0
        for (index in shifted.indices) {
            ret[index] = ProfileValue(elapsed, toMgdl(shifted[index].amount, units))
            elapsed += T.msecs(shifted[index].duration).secs().toInt()
        }
        return ret
    }

    private fun getValues(block: List<Block>, multiplier: Double): Array<ProfileValue> {
        val shifted = block.shiftBlock(multiplier, timeshift)
        val ret = Array(shifted.size) { ProfileValue(0, 0.0) }
        var elapsed = 0
        for (index in shifted.indices) {
            ret[index] = ProfileValue(elapsed, shifted[index].amount)
            elapsed += T.msecs(shifted[index].duration).secs().toInt()
        }
        return ret
    }

    override fun getSingleTargetsMgdl(): Array<ProfileValue> {
        val shifted = targetBlocks.shiftTargetBlock(timeshift)
        val ret = Array(shifted.size) { ProfileValue(0, 0.0) }
        var elapsed = 0
        for (index in shifted.indices) {
            ret[index] = ProfileValue(elapsed, toMgdl((shifted[index].lowTarget + shifted[index].highTarget) / 2.0, units))
            elapsed += T.msecs(shifted[index].duration).secs().toInt()
        }
        return ret
    }

    private fun getValuesList(array: List<Block>, multiplier: Double, format: DecimalFormat, units: String, dateUtil: DateUtil): String =
        StringBuilder().also { sb ->
            var elapsedSec = 0
            array.shiftBlock(multiplier, timeshift).forEach {
                if (elapsedSec != 0) sb.append("\n")
                sb.append(dateUtil.formatHHMM(elapsedSec))
                    .append("    ")
                    .append(format.format(it.amount * multiplier))
                    .append(" $units")
                elapsedSec += T.msecs(it.duration).secs().toInt()
            }
        }.toString()

    private fun getTargetValuesList(array: List<TargetBlock>, format: DecimalFormat, units: String, dateUtil: DateUtil): String =
        StringBuilder().also { sb ->
            var elapsedSec = 0
            array.shiftTargetBlock(timeshift).forEach {
                if (elapsedSec != 0) sb.append("\n")
                sb.append(dateUtil.formatHHMM(elapsedSec))
                    .append("    ")
                    .append(format.format(it.lowTarget))
                    .append(" - ")
                    .append(format.format(it.highTarget))
                    .append(" $units")
                elapsedSec += T.msecs(it.duration).secs().toInt()
            }
        }.toString()

    fun isInProgress(dateUtil: DateUtil): Boolean =
        dateUtil.now() in timestamp..timestamp + (duration ?: 0L)

    private fun toMgdl(value: Double, units: GlucoseUnit): Double =
        if (units == GlucoseUnit.MGDL) value else value * GlucoseUnit.MMOLL_TO_MGDL
}
