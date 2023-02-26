package info.nightscout.plugins.sync.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.interfaces.profile.Profile
import info.nightscout.plugins.sync.tidepool.comm.TidepoolUploader
import info.nightscout.shared.utils.DateUtil
import java.util.UUID

class ProfileElement(ps: EffectiveProfileSwitch, serialNumber: String, dateUtil: DateUtil)
    : BaseElement(ps.timestamp, UUID.nameUUIDFromBytes(("AAPS-profile" + ps.timestamp).toByteArray()).toString(), dateUtil) {

    @Expose
    internal var activeSchedule = "Normal"
    @Expose
    internal var basalSchedules: BasalProfile = BasalProfile()
    @Expose
    internal var units: Units = Units()
    @Expose
    internal var bgTargets: TargetProfile = TargetProfile()
    @Expose
    internal var carbRatios: IcProfile = IcProfile()
    @Expose
    internal var insulinSensitivities: IsfProfile = IsfProfile()
    @Expose
    internal var deviceId: String? = TidepoolUploader.PUMP_TYPE + ":" + serialNumber
    @Expose
    internal var deviceSerialNumber: String = serialNumber
    @Expose
    internal var clockDriftOffset: Long = 0
    @Expose
    internal var conversionOffset: Long = 0

    init {
        type = "pumpSettings"
        val profile: Profile = ProfileSealed.EPS(ps)
        // for (br in profile.getBasalValues())
        //     basalSchedules.Normal.add(BasalRate(br.timeAsSeconds * 1000, br.value))
        // for (target in profile.getSingleTargetsMgdl())
        //     bgTargets.Normal.add(Target(target.timeAsSeconds * 1000, target.value.toInt()))
        // for (ic in profile.getIcsValues())
        //     carbRatios.Normal.add(Ratio(ic.timeAsSeconds * 1000, ic.value.toInt()))
        // for (isf in profile.getIsfsMgdlValues())
        //     insulinSensitivities.Normal.add(Ratio(isf.timeAsSeconds * 1000, isf.value.toInt()))
        for (hour in 0..23) {
            val seconds = hour * 3600
            basalSchedules.Normal.add(BasalRate(seconds * 1000, profile.getBasalTimeFromMidnight(seconds)))
            bgTargets.Normal.add(Target(seconds * 1000, Profile.toMgdl((((profile.getTargetLowMgdlTimeFromMidnight(seconds) + profile.getTargetLowMgdlTimeFromMidnight(seconds))) / 2)).toInt()))
            carbRatios.Normal.add(Ratio(seconds * 1000, profile.getIcTimeFromMidnight(seconds).toInt()))
            insulinSensitivities.Normal.add(Ratio(seconds * 1000, profile.getIsfMgdlTimeFromMidnight(seconds).toInt()))
        }
    }

    inner class BasalProfile internal constructor(
            @field:Expose
            internal var Normal: ArrayList<BasalRate> = ArrayList() // must be the same var name as activeSchedule
    )

    inner class BasalRate internal constructor(
            @field:Expose
            internal var start: Int,
            @field:Expose
            internal var rate: Double
    )

    inner class Units internal constructor(
            @field:Expose
            internal var carb: String = "grams",
            @field:Expose
            internal var bg: String = "mg/dL"
    )

    inner class TargetProfile internal constructor(
            @field:Expose
            internal var Normal: ArrayList<Target> = ArrayList() // must be the same var name as activeSchedule
    )

    inner class Target internal constructor(
            @field:Expose
            internal var start: Int,
            @field:Expose
            internal var target: Int
    )

    inner class IcProfile internal constructor(
            @field:Expose
            internal var Normal: ArrayList<Ratio> = ArrayList() // must be the same var name as activeSchedule
    )

    inner class IsfProfile internal constructor(
            @field:Expose
            internal var Normal: ArrayList<Ratio> = ArrayList() // must be the same var name as activeSchedule
    )

    inner class Ratio internal constructor(
            @field:Expose
            internal var start: Int,
            @field:Expose
            internal var amount: Int
    )
}
