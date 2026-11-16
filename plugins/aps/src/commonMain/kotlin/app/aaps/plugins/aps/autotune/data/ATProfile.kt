package app.aaps.plugins.aps.autotune.data

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.blockValueBySeconds
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.extensions.with
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.autotune.NonFiniteNumberException
import app.aaps.plugins.aps.autotune.OrgJsonText
import app.aaps.plugins.aps.autotune.finite
import dev.zacsweers.metro.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.min

@Inject
class ATProfile(
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
    private val dateUtil: DateUtil,
    private val rh: TextResolver,
    private val profileStoreProvider: () -> ProfileStore,
    private val aapsLogger: AAPSLogger
) {

    lateinit var profile: ProfileSealed
    lateinit var iCfg: ICfg
    lateinit var circadianProfile: ProfileSealed
    private lateinit var pumpProfile: ProfileSealed
    var profileName: String = ""
    var basal = DoubleArray(24)
    var basalUnTuned = IntArray(24)
    var ic = 0.0
    var isf = 0.0
    var dia = 0.0
    var peak = 0
    var isValid: Boolean = false
    var from: Long = 0
    private var pumpProfileAvgISF = 0.0
    private var pumpProfileAvgIC = 0.0

    val icSize: Int
        get() = profile.getIcsValues().size
    val isfSize: Int
        get() = profile.getIsfsMgdlValues().size
    private val avgISF: Double
        get() = if (profile.getIsfsMgdlValues().size == 1) profile.getIsfsMgdlValues()[0].value else Round.roundTo(averageProfileValue(profile.getIsfsMgdlValues()), 0.01)
    private val avgIC: Double
        get() = if (profile.getIcsValues().size == 1) profile.getIcsValues()[0].value else Round.roundTo(averageProfileValue(profile.getIcsValues()), 0.01)

    fun with(profile: Profile, iCfg: ICfg): ATProfile {
        this.profile = profile as ProfileSealed
        this.iCfg = iCfg

        circadianProfile = profile
        isValid = profile.isValid
        if (isValid) {
            //initialize tuned value with current profile values
            var minBasal = 1.0
            for (h in 0..23) {
                basal[h] = Round.roundTo(profile.basalBlocks.blockValueBySeconds(T.hours(h.toLong()).secs().toInt(), 1.0, 0), 0.001)
                minBasal = min(minBasal, basal[h])
            }
            ic = avgIC
            isf = avgISF
            if (ic * isf * minBasal == 0.0)     // Additional validity check to avoid error later in AutotunePrep
                isValid = false
            pumpProfile = profile
            pumpProfileAvgIC = avgIC
            pumpProfileAvgISF = avgISF
        }
        dia = iCfg.dia
        peak = iCfg.peak
        return this
    }

    fun getBasal(timestamp: Long): Double = basal[MidnightTime.secondsFromMidnight(timestamp, SystemTimeZone.current()) / 3600]

    // for local profile synchronisation
    // kotlinx, not org.json: these feed blockFromJson in AutotunePlugin, the last caller the org.json
    // adapters had. The builders below were already kotlinx and only wrapped the result in a JSONArray
    // via a string round trip.
    fun basal(): JsonArray = jsonArrayOf(basal)
    fun ic(circadian: Boolean = false): JsonArray {
        if (circadian)
            return jsonArrayOf(pumpProfile.icBlocks, avgIC / pumpProfileAvgIC)
        return jsonArrayOf(ic)
    }

    fun isf(circadian: Boolean = false): JsonArray {
        if (circadian)
            return jsonArrayOf(pumpProfile.isfBlocks, avgISF / pumpProfileAvgISF)
        return jsonArrayOf(profileUtil.fromMgdlToUnits(isf, profile.units))
    }

    fun getProfile(circadian: Boolean = false): PureProfile {
        return if (circadian)
            circadianProfile.convertToNonCustomizedProfile(dateUtil)
        else
            profile.convertToNonCustomizedProfile(dateUtil)
    }

    fun updateProfile() {
        data()?.let { profile = ProfileSealed.Pure(value = it, activePlugin = null) }
        data(true)?.let { circadianProfile = ProfileSealed.Pure(value = it, activePlugin = null) }
    }

    /**
     * The profile as an oref0 autotune profile file, with min_5m_carbimpact, the insulin curve and a
     * single value for carb_ratio and isf.
     *
     * Written as `org.json` wrote it (see [OrgJsonText]), because the file is compared with oref0's
     * own. A value JSON cannot hold gives an empty text and a logged error, as before.
     */
    fun profileToOrefJSON(): String {
        val insulinType = InsulinType.fromPeak(iCfg.peak * 60000L)
        return try {
            val json = buildJsonObject {
                put("name", profileName)
                put("min_5m_carbimpact", finite(preferences.get(DoubleKey.ApsAmaMin5MinCarbsImpact)))
                put("dia", finite(dia))
                if (insulinType == InsulinType.OREF_ULTRA_RAPID_ACTING)
                    put("curve", "ultra-rapid")
                else if (insulinType == InsulinType.OREF_RAPID_ACTING)
                    put("curve", "rapid-acting")
                else {
                    val peakTime: Int = iCfg.peak
                    put("curve", if (peakTime > 50) "rapid-acting" else "ultra-rapid")
                    put("useCustomPeakTime", true)
                    put("insulinPeakTime", peakTime)
                }
                put("basalprofile", buildJsonArray {
                    for (h in 0..23) {
                        val secondFromMidnight = h * 60 * 60
                        val time: String = NumberFormat.INTEGER_2_DIGITS.format(h) + ":00:00"
                        add(buildJsonObject {
                            put("start", time)
                            put("minutes", h * 60)
                            put("rate", finite(profile.getBasalTimeFromMidnight(secondFromMidnight)))
                        })
                    }
                })
                val isfValue = Round.roundTo(avgISF, 0.001)
                put("isfProfile", buildJsonObject {
                    put("sensitivities", buildJsonArray {
                        add(buildJsonObject {
                            put("i", 0)
                            put("start", "00:00:00")
                            put("sensitivity", finite(isfValue))
                            put("offset", 0)
                            put("x", 0)
                            put("endoffset", 1440)
                        })
                    })
                })
                put("carb_ratio", finite(avgIC))
                put("autosens_max", finite(preferences.get(DoubleKey.AutosensMax)))
                put("autosens_min", finite(preferences.get(DoubleKey.AutosensMin)))
                put("units", GlucoseUnit.MGDL.asText)
                put("timezone", SystemTimeZone.current().id)
            }
            OrgJsonText.render(json, 2).replace("\\/", "/")
        } catch (e: NonFiniteNumberException) {
            aapsLogger.error(LTag.CORE, e.stackTraceToString())
            ""
        }
    }

    /**
     * The pump profile with the tuned schedules put over it.
     *
     * `toPureNsJson` already answers a kotlinx document, so this used to render it to text and parse
     * it back as `org.json` purely to overwrite three keys. It stays on kotlinx throughout now, which
     * also removes the `try`/`catch`: nothing here throws, because a document is built rather than
     * written into.
     */
    fun data(circadian: Boolean = false): PureProfile? {
        // org.json refused NaN and Infinity outright, so a non finite tuned value used to abort the
        // document and this answered null. kotlinx writes the bare token NaN instead, and the lenient
        // reader parses it straight back, which would put a NaN into a dosing profile. Fail the same
        // way it used to.
        if (!isf.isFinite() || !ic.isFinite() || basal.any { !it.isFinite() }) {
            aapsLogger.error(LTag.CORE, "Autotune produced a non finite value, profile rejected")
            return null
        }
        val json = profile.toPureNsJson(dateUtil).with {
            if (circadian) {
                put("sens", jsonArrayOf(pumpProfile.isfBlocks, avgISF / pumpProfileAvgISF))
                put("carbratio", jsonArrayOf(pumpProfile.icBlocks, avgIC / pumpProfileAvgIC))
            } else {
                put("sens", jsonArrayOf(profileUtil.fromMgdlToUnits(isf, profile.units)))
                put("carbratio", jsonArrayOf(ic))
            }
            put("basal", jsonArrayOf(basal))
        }
        return pureProfileFromJson(json, dateUtil, profile.units.asText)
    }

    /**
     * A profile store that holds only the tuned profile, for a profile switch to it.
     *
     * It used to pass through `org.json` text, which wrote a whole number without its fraction and
     * then read it back. The store reads every schedule value as a double, so building it on kotlinx
     * directly gives the same profile. A value JSON cannot hold gives no store and a logged error, as
     * `org.json` refused it before.
     */
    fun profileStore(circadian: Boolean = false): ProfileStore? {
        val tunedProfile = if (circadian) circadianProfile else profile
        if (profileName.isEmpty())
            profileName = rh.gs(ApsStrings.autotune_tunedprofile_name)
        val pureJson = tunedProfile.toPureNsJson(dateUtil)
        if (pureJson.hasNonFiniteNumber()) {
            aapsLogger.error(LTag.CORE, "Autotune produced a non finite value, profile store rejected")
            return null
        }
        val json = buildJsonObject {
            put("defaultProfile", profileName)
            put("store", buildJsonObject { put(profileName, pureJson) })
            put("startDate", dateUtil.toISOAsUTC(dateUtil.now()))
        }
        return profileStoreProvider().with(json)
    }

    private fun JsonElement.hasNonFiniteNumber(): Boolean = when (this) {
        is JsonObject    -> values.any { it.hasNonFiniteNumber() }
        is JsonArray     -> any { it.hasNonFiniteNumber() }
        is JsonPrimitive -> !isString && doubleOrNull?.isFinite() == false
    }

    /**
     * The hourly basal as a profile schedule, rounded to 0.01 U/h. Autotune itself works with three
     * decimals ([basal]), but no pump AAPS drives takes a finer basal step than 0.01, and the profile
     * editor shows two decimals. With the third one kept, the editor stepped and summed digits the user
     * could not see (#5401, #5402).
     */
    private fun jsonArrayOf(values: DoubleArray): JsonArray =
        buildJsonArray {
            for (h in 0..23) {
                add(
                    buildJsonObject {
                        put("time", NumberFormat.INTEGER_2_DIGITS.format(h.toLong()) + ":00")
                        put("timeAsSeconds", h * 60 * 60)
                        put("value", Round.roundTo(values[h], 0.01))
                    }
                )
            }
        }

    private fun jsonArrayOf(value: Double): JsonArray =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("time", "00:00")
                    put("timeAsSeconds", 0)
                    put("value", value)
                }
            )
        }

    private fun jsonArrayOf(values: List<Block>, multiplier: Double = 1.0): JsonArray =
        buildJsonArray {
            var elapsedHours = 0L
            values.forEach {
                val value = values.blockValueBySeconds(T.hours(elapsedHours).secs().toInt(), multiplier, 0)
                add(
                    buildJsonObject {
                        put("time", NumberFormat.INTEGER_2_DIGITS.format(elapsedHours) + ":00")
                        put("timeAsSeconds", T.hours(elapsedHours).secs())
                        put("value", value)
                    }
                )
                elapsedHours += T.msecs(it.duration).hours()
            }
        }

    companion object {

        fun averageProfileValue(pf: Array<Profile.ProfileValue>?): Double {
            var avgValue = 0.0
            val secondPerDay = 24 * 60 * 60
            if (pf == null) return avgValue
            for (i in pf.indices) {
                avgValue += pf[i].value * ((if (i == pf.size - 1) secondPerDay else pf[i + 1].timeAsSeconds) - pf[i].timeAsSeconds)
            }
            avgValue /= secondPerDay.toDouble()
            return avgValue
        }
    }
}
