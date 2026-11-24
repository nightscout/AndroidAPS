package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.PS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSICfg
import app.aaps.core.nssdk.localmodel.treatment.NSProfileSwitch
import app.aaps.core.objects.extensions.getCustomizedName
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.lenientDoubleOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * [insulinFallback] stamps records uploaded without one — see `NsIncomingDataProcessor.fallbackICfg`. It gets
 * the DIA the record carries itself, see [diaInProfileJson].
 */
fun NSProfileSwitch.toProfileSwitch(profileRepository: ProfileRepository, dateUtil: DateUtil, insulinFallback: (recordDia: Double?) -> ICfg): PS? {
    val pureProfile =
        profileJson?.let { pureProfileFromJson(it, dateUtil) ?: return null }
            ?: profileRepository.profile.value?.getSpecificProfile(profile) ?: return null

    val profileSealed = ProfileSealed.Pure(value = pureProfile, activePlugin = null)
    val iCfg =
        iCfg?.let {
            ICfg(insulinLabel = it.insulinLabel, insulinEndTime = it.insulinEndTime, insulinPeakTime = it.insulinPeakTime, concentration = it.concentration)
        } ?: insulinFallback(diaInProfileJson(profileJson))

    return PS(
        isValid = isValid,
        timestamp = date ?: throw IllegalArgumentException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = profileSealed.units,
        profileName = originalProfileName ?: profile,
        timeshift = timeShift ?: 0,
        percentage = percentage ?: 100,
        // originalDuration is always written in milliseconds by AAPS and is the trustworthy source.
        // Documents uploaded before 9b90eea774 carry a duration inflated by 60000 and no
        // durationInMilliseconds, which TreatmentMapper then inflates by 60000 once more.
        duration = originalDuration ?: duration ?: 0L,
        iCfg = iCfg,
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )
}

/**
 * The DIA in hours that a profile switch uploaded by AAPS 3.x carries in its profile JSON, or null.
 *
 * Up to 3.x the DIA belonged to the profile, so `ProfileSealed.toPureNsJson` wrote it as `"dia"` into every
 * profile switch and effective profile switch it uploaded. 4.0 moved it to the insulin and sends it as `iCfg`,
 * so this is read only for a record that came without one. It is the DIA that record was dosed with.
 */
internal fun diaInProfileJson(profileJson: String?): Double? =
    profileJson
        ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        .lenientDoubleOrNull("dia")
        ?.takeIf { it > 0.0 }

fun PS.toNSProfileSwitch(dateUtil: DateUtil, decimalFormatter: DecimalFormatter): NSProfileSwitch {
    val unmodifiedCustomizedName = getCustomizedName(decimalFormatter)
    // ProfileSealed.PS doesn't provide unmodified json -> reset it
    val notCustomized = this.copy()
    notCustomized.timeshift = 0
    notCustomized.percentage = 100

    return NSProfileSwitch(
        eventType = EventType.PROFILE_SWITCH,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        timeShift = timeshift,
        percentage = percentage,
        duration = duration,
        profile = unmodifiedCustomizedName,
        originalProfileName = profileName,
        originalDuration = duration,
        profileJson = ProfileSealed.PS(value = notCustomized, activePlugin = null).toPureNsJson(dateUtil).toString(),
        identifier = ids.nightscoutId,
        pumpId = ids.pumpId,
        pumpType = ids.pumpType?.name,
        pumpSerial = ids.pumpSerial,
        endId = ids.endId,
        iCfg = NSICfg(insulinLabel = iCfg.insulinLabel, insulinEndTime = iCfg.insulinEndTime, insulinPeakTime = iCfg.insulinPeakTime, concentration = iCfg.concentration)
    )
}