package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader
import info.nightscout.androidaps.utils.InstanceId
import java.util.*
import kotlin.collections.ArrayList

class ProfileElement private constructor(ps: ProfileSwitch)
    : BaseElement(ps.date, UUID.nameUUIDFromBytes(("AAPS-profile" + ps.date).toByteArray()).toString()) {

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
    internal var deviceId: String = TidepoolUploader.PUMPTYPE + ":" + (ConfigBuilderPlugin.getPlugin().activePump?.serialNumber()
            ?: InstanceId.instanceId())
    @Expose
    internal var deviceSerialNumber: String = ConfigBuilderPlugin.getPlugin().activePump?.serialNumber()
            ?: InstanceId.instanceId()
    @Expose
    internal var clockDriftOffset: Long = 0
    @Expose
    internal var conversionOffset: Long = 0

    init {
        type = "pumpSettings"
        val profile: Profile? = ps.profileObject
        checkNotNull(profile)
        for (br in profile.basalValues)
            basalSchedules.Normal.add(BasalRate(br.timeAsSeconds * 1000, br.value))
        for (target in profile.singleTargets)
            bgTargets.Normal.add(Target(target.timeAsSeconds * 1000, Profile.toMgdl(target.value, profile.units)))
        for (ic in profile.ics)
            carbRatios.Normal.add(Ratio(ic.timeAsSeconds * 1000, ic.value))
        for (isf in profile.isfs)
            insulinSensitivities.Normal.add(Ratio(isf.timeAsSeconds * 1000, Profile.toMgdl(isf.value, profile.units)))
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
            internal var target: Double
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
            internal var amount: Double
    )

    companion object {
        @JvmStatic
        fun newInstanceOrNull(ps: ProfileSwitch): ProfileElement? = try {
            ProfileElement(ps)
        } catch (e: Throwable) {
            null
        }
    }

}


