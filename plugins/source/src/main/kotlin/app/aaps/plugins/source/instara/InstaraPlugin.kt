package app.aaps.plugins.source.instara

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.R
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.source.AbstractBgSourcePlugin
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstaraPlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .pluginIcon(R.drawable.ic_generic_cgm)
        .preferencesId(PluginDescription.Companion.PREFERENCE_SCREEN)
        .pluginName(app.aaps.plugins.source.R.string.instara_app)
        .preferencesVisibleInSimpleMode(false)
        .description(app.aaps.plugins.source.R.string.description_source_instara_app),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, config
), BgSource {

    // cannot be inner class because of needed injection
    class InstaraWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var instaraPlugin: InstaraPlugin
        @Inject lateinit var persistenceLayer: PersistenceLayer

        // Minimal addition: used to request Overview refresh after successful insert/update.
        @Inject lateinit var rxBus: RxBus

        private fun readDouble(json: JSONObject, vararg keys: String): Double {
            for (k in keys) if (json.has(k) && !json.isNull(k)) return json.getDouble(k)
            throw JSONException("Missing glucose value field among: ${keys.joinToString()}")
        }

        private fun normalizeToMgdl(value: Double, raw: Double?, unitsStr: String?): Pair<Double, Double?> {
            val units = unitsStr?.trim()?.lowercase()
            val mmolToMgdl = Constants.MMOLL_TO_MGDL // 18.0

            // If units explicitly provided, honor it.
            return when (units) {
                "mmol", "mmol/l"                   -> Pair(value * mmolToMgdl, raw?.let { it * mmolToMgdl })
                "mg/dl", "mgdl", "mg per dl", "mg" -> Pair(value, raw)

                else                               -> {
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
            if (!instaraPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))

            try {
                val rawData = inputData.getString("data") ?: return Result.failure(workDataOf("Error" to "missing data"))
                val jsonArray = JSONArray(rawData)
                aapsLogger.debug(LTag.BGSOURCE, "Instara CGM batch size: ${jsonArray.length()}")

                val glucoseValues = mutableListOf<GV>()
                val nowMs = System.currentTimeMillis()
                var hasRecentInBatch = false

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)

                    val ts = json.getLong("date")
                    if (!hasRecentInBatch && (nowMs - ts) in 0..INSTARA_RECENT_WINDOW_MS) {
                        hasRecentInBatch = true
                    }
                    val unitsField = json.optString("units", null)

                    // Accept either "current" or legacy "sgv" as the primary value
                    val current = readDouble(json, "current", "sgv")
                    // "raw" is optional; if absent, we keep it null
                    val raw = if (json.has("raw") && !json.isNull("raw")) json.getDouble("raw") else null

                    val (mgdl, rawMgdl) = normalizeToMgdl(current, raw, unitsField)

                    // Use "NONE" as fallback value
                    val direction: String =
                        json.optString("direction")
                            .trim()
                            .takeIf { it.isNotEmpty() && json.has("direction") && !json.isNull("direction") }
                            ?: "NONE"

                    glucoseValues += GV(
                        timestamp = ts,
                        value = mgdl,            // always mg/dL in GV.value
                        raw = rawMgdl,           // null or mg/dL
                        noise = null,
                        trendArrow = TrendArrow.fromString(direction),
                        sourceSensor = SourceSensor.INSTARA
                    )
                }

                // Upstream changed this to suspend; call directly and catch exceptions.
                persistenceLayer.insertCgmSourceData(Sources.Instara, glucoseValues, emptyList(), null)

                // Minimal Instara-only UI refresh request after successful DB insert/update.
                // EventRefreshOverview requires a "from" string.
                if (hasRecentInBatch) {
                    rxBus.send(EventRefreshOverview(from = "Instara", now = true))
                }

            } catch (e: JSONException) {
                aapsLogger.error("Exception: ", e)
                ret = Result.failure(workDataOf("Error" to e.toString()))
            } catch (t: Throwable) {
                ret = Result.failure(workDataOf("Error" to t.toString()))
            }

            return ret
        }

        companion object {

            private const val INSTARA_RECENT_WINDOW_MS = 3L * 60_000L
        }
    }
}