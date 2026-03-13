package app.aaps.plugins.source.teljane

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.R
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.source.AbstractBgSourcePlugin
import app.aaps.plugins.source.BGSourceFragment
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.core.ui.R as CoreUiR

@Singleton
class TeljanePlugin @Inject constructor(
    private val application: Application,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(R.drawable.ic_generic_cgm)
        .preferencesId(PluginDescription.Companion.PREFERENCE_SCREEN)
        .pluginName(app.aaps.plugins.source.R.string.teljane_app)
        .preferencesVisibleInSimpleMode(false)
        .description(app.aaps.plugins.source.R.string.description_source_teljane_app),
    ownPreferences = emptyList(),
    aapsLogger = aapsLogger,
    rh = rh,
    preferences = preferences
), BgSource {

    private fun appContext(): Context = application.applicationContext

    /**
     * Add Teljane-only preference UI.
     * AbstractBgSourcePlugin adds the generic BG source settings.
     * We append a Teljane-specific switch that controls the history request timer worker.
     */
    override fun addPreferenceScreen(
        preferenceManager: PreferenceManager,
        parent: PreferenceScreen,
        context: Context,
        requiredKey: String?
    ) {
        // Keep generic BG source settings (e.g. Upload to NS)
        super.addPreferenceScreen(preferenceManager, parent, context, requiredKey)

        // AAPS convention: if requiredKey != null, do not add more settings here
        if (requiredKey != null) return

        val category = PreferenceCategory(context).apply {
            key = "teljane_history_request_settings"
            title = rh.gs(CoreUiR.string.teljane_history_request_settings_title)
            initialExpandedChildrenCount = 0
        }
        parent.addPreference(category)

        category.addPreference(
            AdaptiveSwitchPreference(
                ctx = context,
                booleanKey = BooleanKey.TeljaneHistoryRequestEnabled,
                title = CoreUiR.string.teljane_history_request_enabled_title
            )
        )
    }

    // PluginBase calls these when enabled/disabled via setPluginEnabled()
    override fun onStart() {
        super.onStart()
        // Use the setting to control the timer scheduling.
        // NOTE: "preferences" comes from PluginBaseWithPreferences (supertype), do NOT redeclare it here.
        val enabled = this.preferences.get(BooleanKey.TeljaneHistoryRequestEnabled)
        TeljaneStaleCheckWorker.ensureScheduled(appContext(), enabled = enabled)
    }

    override fun onStop() {
        TeljaneStaleCheckWorker.cancel(appContext())
        super.onStop()
    }

    // cannot be inner class because of needed injection
    class TeljaneWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var teljanePlugin: TeljanePlugin
        @Inject lateinit var persistenceLayer: PersistenceLayer

        private fun readDouble(json: JSONObject, vararg keys: String): Double {
            for (k in keys) if (json.has(k) && !json.isNull(k)) return json.getDouble(k)
            throw JSONException("Missing glucose value field among: ${keys.joinToString()}")
        }

        /**
         * Teljane sends sgvId as a 13-digit string.
         * We store it as Long in AAPS.
         */
        private fun parseSgvId(json: JSONObject): Long? {
            val v = json.optString("sgvId", null)?.trim()
            if (v.isNullOrEmpty() || v.length != TELJANE_SGVID_DIGITS || !v.all { it.isDigit() }) return null
            return v.toLongOrNull()
        }

        /**
         * Teljane mark:
         * - accepts any mark >= 0
         * - must be 5 digits max (<= 99999)
         * - if > 99999: log debug and fallback to 6048
         * - if missing/null or invalid: return null (device-level fallback handled elsewhere)
         */
        private fun parseSgvMark(json: JSONObject): Int? {
            if (!json.has("sgvMark") || json.isNull("sgvMark")) return null

            // can be int or string in vendor payloads
            val mark: Int? = try {
                when (val any = json.get("sgvMark")) {
                    is Number -> any.toInt()
                    is String -> any.trim().toIntOrNull()
                    else -> null
                }
            } catch (_: Throwable) { null }

            // negative / unparseable -> treat as missing
            if (mark == null || mark < 0) return null

            if (mark > TELJANE_MARK_MAX) {
                // do not reject the record; fallback to default for safety
                aapsLogger.debug(
                    LTag.BGSOURCE,
                    "Teljane CGM: sgvMark out of range (> $TELJANE_MARK_MAX): $mark -> fallback=$TELJANE_MARK_FALLBACK"
                )
                return TELJANE_MARK_FALLBACK
            }

            return mark
        }

        private fun normalizeToMgdl(value: Double, raw: Double?, unitsStr: String?): Pair<Double, Double?> {
            val units = unitsStr?.trim()?.lowercase()
            val mmolToMgdl = Constants.MMOLL_TO_MGDL // 18.0

            // If units explicitly provided, honor it.
            return when (units) {
                "mmol", "mmol/l" -> Pair(value * mmolToMgdl, raw?.let { it * mmolToMgdl })
                "mg/dl", "mgdl", "mg per dl", "mg" -> Pair(value, raw)
                else -> {
                    // No (or unknown) units: infer by magnitude
                    // Heuristic: < 40 → mmol/L, otherwise mg/dL
                    if (value < 40.0) Pair(value * mmolToMgdl, raw?.let { if (it < 40.0) it * mmolToMgdl else it })
                    else Pair(value, raw)
                }
            }
        }

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()
            if (!teljanePlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))

            try {
                val rawData = inputData.getString("data") ?: return Result.failure(workDataOf("Error" to "missing data"))
                val jsonArray = JSONArray(rawData)
                aapsLogger.debug(LTag.BGSOURCE, "Teljane CGM batch size: ${jsonArray.length()}")

                val glucoseValues = mutableListOf<GV>()

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)

                    val ts = json.getLong("date")
                    val unitsField = json.optString("units", null)

                    // Accept either "current" or legacy "sgv" as the primary value
                    val current = readDouble(json, "current", "sgv")
                    // "raw" is optional; if absent, we keep it null
                    val raw = if (json.has("raw") && !json.isNull("raw")) json.getDouble("raw") else null

                    val (mgdl, rawMgdl) = normalizeToMgdl(current, raw, unitsField)

                    val direction = json.optString("direction", "Flat")

                    // Teljane extras
                    val sgvId = parseSgvId(json)      // Long?
                    val sgvMark = parseSgvMark(json)  // Int? (or fallback if >99999)

                    val gv = GV(
                        timestamp = ts,
                        value = mgdl,            // always mg/dL in GV.value
                        raw = rawMgdl,           // null or mg/dL
                        noise = null,
                        trendArrow = TrendArrow.fromString(direction),
                        sourceSensor = SourceSensor.TELJANE
                    )

                    // Teljane extras saved into db
                    gv.sgvId = sgvId
                    gv.sgvMark = sgvMark
                    glucoseValues += gv
                }

                persistenceLayer.insertCgmSourceData(Sources.Teljane, glucoseValues, emptyList(), null)
                    .doOnError { err ->
                        ret = Result.failure(workDataOf("Error" to err.toString()))
                    }
                    .blockingGet()

            } catch (e: JSONException) {
                aapsLogger.error("Exception: ", e)
                ret = Result.failure(workDataOf("Error" to e.toString()))
            }

            return ret
        }

        companion object {
            private const val TELJANE_SGVID_DIGITS = 13
            private const val TELJANE_MARK_MAX = 99_999
            private const val TELJANE_MARK_FALLBACK = 6_048
        }
    }
}