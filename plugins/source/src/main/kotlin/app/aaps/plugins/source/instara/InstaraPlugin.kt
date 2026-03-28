package app.aaps.plugins.source.instara

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
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.R
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.source.AbstractBgSourcePlugin
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.core.ui.R as CoreUiR

@Singleton
class InstaraPlugin @Inject constructor(
    private val application: Application,
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

    private fun appContext(): Context = application.applicationContext

    // Preference Settings
    override fun addPreferenceScreen(
        preferenceManager: PreferenceManager,
        parent: PreferenceScreen,
        context: Context,
        requiredKey: String?
    ) {
        super.addPreferenceScreen(preferenceManager, parent, context, requiredKey)
        if (requiredKey != null) return

        // Instara Category
        val category = PreferenceCategory(context).apply {
            key = "instara_history_request_settings"
            title = rh.gs(CoreUiR.string.instara_history_request_settings_title)
            initialExpandedChildrenCount = 0
        }
        parent.addPreference(category)

        // Instara history request switch
        category.addPreference(
            AdaptiveSwitchPreference(
                ctx = context,
                booleanKey = BooleanKey.InstaraHistoryRequestEnabled,
                title = CoreUiR.string.instara_history_request_enabled_title
            )
        )
    }

    override fun onStart() {
        super.onStart()
        // Keep the worker scheduled while this plugin is enabled.
        // The worker itself decides OFF(skip+reschedule) vs ON(run+reschedule).
        InstaraStaleCheckWorker.ensureScheduled(appContext(), enabled = true)
    }

    override fun onStop() {
        // Cancel worker when this plugin is disabled.
        InstaraStaleCheckWorker.cancel(appContext())
        super.onStop()
    }

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

        // Needed to persist per-device Instara meta into preferences
        @Inject lateinit var preferences: Preferences

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

        /**
         * Instara requirement:
         * - sgvId MUST be a 13-digit number string.
         * - If malformed/missing -> record is INVALID -> DO NOT insert into DB.
         */
        private fun parseSgvIdStrict(json: JSONObject): Long? {
            val v = json.optString("sgvId", null)?.trim() ?: return null
            if (v.length != 13) return null
            if (!v.all { it.isDigit() }) return null
            return v.toLongOrNull()
        }

        private fun parseSgvMark(json: JSONObject): Int? {
            if (!json.has("sgvMark") || json.isNull("sgvMark")) return null
            val mark: Int? = try {
                when (val any = json.get("sgvMark")) {
                    is Number -> any.toInt()
                    is String -> any.trim().toIntOrNull()
                    else      -> null
                }
            } catch (_: Throwable) {
                null
            }
            if (mark == null || mark !in 0..99_999) return null
            return mark
        }

        private fun devicePrefixOf(sgvId: Long): Long = sgvId / 100000L

        /**
         * Persist Instara meta (LATEST DEVICE ONLY, NO DB schema changes).
         *
         * Policy:
         * 1) Same devicePrefix: first one wins -> NEVER update existing sgvStart/sgvMark.
         * 2) New devicePrefix: overwrite (keep latest device only).
         * JSON format example:
         * { "31000399": {"sgvStart": 3100039900003, "sgvMark": 6048} }
         */
        private fun overwriteDeviceMeta(devicePrefix: Long, sgvStart: Long, sgvMark: Int) {
            // Read existing meta JSON
            val raw = preferences.get(StringNonKey.InstaraDeviceMetaJson)
            val existingRoot = try {
                JSONObject(raw)
            } catch (_: Throwable) {
                JSONObject()
            }

            val key = devicePrefix.toString()

            // If the same device already exists in meta, do NOT update (first-one-wins)
            if (existingRoot.has(key)) return

            // Otherwise, overwrite with latest device only
            val root = JSONObject()
            root.put(key, JSONObject().apply {
                put("sgvStart", sgvStart)
                put("sgvMark", sgvMark)
            })
            preferences.put(StringNonKey.InstaraDeviceMetaJson, root.toString())
        }

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()
            if (!instaraPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))

            try {
                val rawData = inputData.getString("data") ?: return Result.failure(workDataOf("Error" to "missing data"))
                val jsonArray = JSONArray(rawData)
                aapsLogger.debug(LTag.BGSOURCE, "Instara CGM batch size: ${jsonArray.length()}")

                val nowMs = System.currentTimeMillis()
                var hasRecentInBatch = false

                // We only keep latest device meta, so track the *latest* devicePrefix we see in this batch,
                // and the first sgvId that carries sgvMark for that device.
                var latestDevicePrefix: Long? = null
                var latestDeviceStartWithMark: Long? = null
                var latestDeviceMark: Int? = null

                // --- DEDUPE (Instara only, no schema changes) ---
                // - pumpId (sgvId) defines identity
                // - DB column is glucoseValues.pumpId (InterfaceIDs.pumpId)
                val seenInBatch = HashSet<Long>()
                val glucoseValues = mutableListOf<GV>()
                var skippedBatchDup = 0
                var skippedDbDup = 0
                var skippedInvalid = 0

                // NOTE: Process records in sgvId ascending order within the same batch
                // This helps deterministic insertion/processing when multiple sequential ids arrive together.
                val order = (0 until jsonArray.length()).toMutableList()
                order.sortWith(compareBy { idx ->
                    val json = jsonArray.getJSONObject(idx)
                    parseSgvIdStrict(json) ?: Long.MAX_VALUE
                })

                for (idx in order) {
                    val json = jsonArray.getJSONObject(idx)

                    // Must have a valid 13-digit sgvId; otherwise record is invalid -> skip DB insert.
                    val sgvId = parseSgvIdStrict(json)
                    if (sgvId == null) {
                        skippedInvalid++
                        aapsLogger.debug(
                            LTag.BGSOURCE,
                            "Instara: invalid/malformed sgvId, skip record: ${json.opt("sgvId")}"
                        )
                        continue
                    }

                    // In-batch dedupe first (same sgvId appears twice in same payload).
                    if (!seenInBatch.add(sgvId)) {
                        skippedBatchDup++
                        aapsLogger.debug(LTag.BGSOURCE, "Instara: duplicate sgvId in same batch -> skip pumpId=$sgvId")
                        continue
                    }

                    // Cross-batch dedupe: if this pumpId already exists in DB -> skip.
                    val exists = persistenceLayer.instaraPumpIdExists(sgvId)
                    if (exists) {
                        skippedDbDup++
                        aapsLogger.debug(LTag.BGSOURCE, "Instara: duplicate sgvId already in DB -> skip pumpId=$sgvId")
                        continue
                    }

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

                    // NOTE: Currently use "NONE" as fallback value
                    val direction: String =
                        json.optString("direction")
                            .trim()
                            .takeIf { it.isNotEmpty() && json.has("direction") && !json.isNull("direction") }
                            ?: "NONE"

                    val sgvMark = parseSgvMark(json)

                    // Instara-specific TrendArrow handling when NONE/invalid:
                    // If Instara provides NONE/invalid, attempt to calculate from previous (sgvId-1) via DB; fallback Flat.
                    val resolvedArrow: TrendArrow =
                        InstaraTrendArrowResolver.resolve(persistenceLayer, direction, mgdl, sgvId)

                    val gv = GV(
                        timestamp = ts,
                        value = mgdl,            // always mg/dL in GV.value
                        raw = rawMgdl,           // null or mg/dL
                        noise = null,
                        trendArrow = resolvedArrow, // Instara: use resolved arrow (may be calculated/fallback)
                        sourceSensor = SourceSensor.INSTARA
                    )

                    /**
                     * IMPORTANT
                     * Store Instara sgvId into InterfaceIDs.pumpId (DB column: glucoseValues.pumpId).
                     * This is used for dedupe + history scan without adding new DB columns.
                     */
                    gv.ids = gv.ids.copy(pumpId = sgvId)

                    // Latest-device meta capture (only when sgvMark exists)
                    if (sgvMark != null) {
                        val prefix = devicePrefixOf(sgvId)
                        latestDevicePrefix = prefix
                        latestDeviceMark = sgvMark
                        // sgvStart defined as the sgvId of the first row that carries sgvMark != null for that device.
                        if (latestDeviceStartWithMark == null || sgvId < latestDeviceStartWithMark!!) {
                            latestDeviceStartWithMark = sgvId
                        }
                    }

                    glucoseValues += gv
                }

                aapsLogger.debug(
                    LTag.BGSOURCE,
                    "Instara: prepared=${glucoseValues.size} skippedInvalid=$skippedInvalid skippedBatchDup=$skippedBatchDup skippedDbDup=$skippedDbDup"
                )

                // If all records were invalid/dupes, do nothing.
                if (glucoseValues.isEmpty()) {
                    aapsLogger.debug(LTag.BGSOURCE, "Instara: no insertable records in batch -> skip insert")
                    return Result.success(workDataOf("Result" to "No insertable records"))
                }

                // Upstream changed this to suspend; call directly and catch exceptions.
                persistenceLayer.insertCgmSourceData(Sources.Instara, glucoseValues, emptyList(), null)
                // UPDATED: txResult no longer exists (upstream suspend call). If we reached here, insert succeeded.
                val insertOk = true

                // Persist latest-device-only meta JSON if we saw a valid mark seed.
                if (latestDevicePrefix != null && latestDeviceStartWithMark != null && latestDeviceMark != null) {
                    // NOTE (updated): overwriteDeviceMeta() already enforces:
                    // - same devicePrefix: first one wins (no update)
                    // - new devicePrefix: overwrite to latest only
                    overwriteDeviceMeta(latestDevicePrefix!!, latestDeviceStartWithMark!!, latestDeviceMark!!)

                    // Ensure worker is scheduled (new device can arrive after previous completion)
                    val enabled = preferences.get(BooleanKey.InstaraHistoryRequestEnabled)
                    InstaraStaleCheckWorker.ensureScheduled(instaraPlugin.appContext(), enabled = enabled)
                }

                // Refresh overview only when we received at least one recent record
                if (insertOk && hasRecentInBatch) {
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