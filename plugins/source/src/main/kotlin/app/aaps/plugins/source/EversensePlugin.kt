package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.ui.compose.icons.IcPluginByoda
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.core.utils.receivers.Inbox
import app.aaps.plugins.source.activities.RequestEversensePermissionActivity
import app.aaps.plugins.source.compose.BgSourceComposeContent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EversensePlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val context: Context,
    preferences: Preferences,
) : AbstractBgSourceWithSensorInsertLogPlugin(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(R.string.byoesa)
            )
        }
        .icon(IcPluginByoda)
        .pluginName(R.string.byoesa)
        .shortName(R.string.byoesa_short)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_byoesa),
    aapsLogger = aapsLogger,
    rh = rh,
    preferences = preferences
), BgSource {

    override fun requiredPermissions(): List<PermissionGroup> =
        if (isByoesaInstalled()) listOf(
            PermissionGroup(
                permissions = listOf(PERMISSION),
                rationaleTitle = R.string.permission_byoesa_title,
                rationaleDescription = R.string.permission_byoesa_description,
                special = true,
            )
        ) else emptyList()

    internal fun isByoesaInstalled(): Boolean =
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (_: Exception) {
            false
        }

    override suspend fun onStart() {
        super.onStart()
        requestPermissionIfNeeded()
    }

    private fun requestPermissionIfNeeded() {
        if (!isByoesaInstalled()) return
        if (ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(context, RequestEversensePermissionActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    @HiltWorker
    class EversenseWorker @AssistedInject constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        aapsLogger: AAPSLogger,
        fabricPrivacy: FabricPrivacy,
        private val eversensePlugin: EversensePlugin,
        private val dateUtil: DateUtil,
        private val dataInbox: DataInbox,
        private val persistenceLayer: PersistenceLayer,
    ) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            // Drain first so DataInbox's pending-work gate is always released.
            val bundles = dataInbox.drain(EversenseInbox)
            if (!eversensePlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            if (bundles.isEmpty()) return Result.success(workDataOf("Result" to "no data"))

            var hadFailure = false
            for ((index, bundle) in bundles.withIndex()) {
                try {
                    val glucoseValues = parseGlucoseValues(bundle, dateUtil.now())
                    if (glucoseValues.isEmpty()) {
                        aapsLogger.warn(LTag.BGSOURCE, "Skipping invalid or empty BYOESA broadcast")
                        continue
                    }
                    persistenceLayer.insertCgmSourceData(Sources.Eversense, glucoseValues, emptyList(), null)
                } catch (e: CancellationException) {
                    dataInbox.requeue(EversenseInbox, bundles.subList(index, bundles.size))
                    throw e
                } catch (e: Exception) {
                    aapsLogger.error("Error while processing intent from BYOESA", e)
                    hadFailure = true
                }
            }
            return if (hadFailure) Result.failure(workDataOf("Error" to "one or more bundles failed")) else Result.success()
        }
    }

    companion object {

        const val PACKAGE_NAME = "com.byoesa.eversense365"
        const val ACTION = "com.byoesa.eversense365.EXTERNAL_BROADCAST"
        const val PERMISSION = "com.byoesa.eversense365.EXTERNAL_PERMISSION"
        const val CONTRACT_VERSION = 1
        const val SENSOR_TYPE = "EVERSENSE_365"

        private const val KEY_CONTRACT_VERSION = "byoesaContractVersion"
        private const val KEY_SENSOR_TYPE = "sensorType"
        private const val KEY_SOURCE_PACKAGE = "sourcePackage"
        private const val KEY_GLUCOSE_VALUES = "glucoseValues"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_GLUCOSE_VALUE = "glucoseValue"
        private const val KEY_TREND_ARROW = "trendArrow"

        internal fun parseGlucoseValues(bundle: Bundle, now: Long): List<GV> {
            if (bundle.getInt(KEY_CONTRACT_VERSION, 0) != CONTRACT_VERSION) return emptyList()
            if (bundle.getString(KEY_SENSOR_TYPE) != SENSOR_TYPE) return emptyList()
            if (bundle.getString(KEY_SOURCE_PACKAGE) != PACKAGE_NAME) return emptyList()

            val readings = bundle.getBundle(KEY_GLUCOSE_VALUES) ?: return emptyList()
            return buildList {
                for (index in 0 until readings.size()) {
                    val reading = readings.getBundle(index.toString()) ?: continue
                    val timestampSeconds = reading.getLong(KEY_TIMESTAMP, 0L)
                    val timestamp = timestampSeconds * 1_000L
                    val glucoseValue = reading.getInt(KEY_GLUCOSE_VALUE, 0)

                    if (timestampSeconds <= 0L || timestamp > now) continue
                    if (glucoseValue !in 1..1_000) continue

                    add(
                        GV(
                            timestamp = timestamp,
                            value = glucoseValue.toDouble(),
                            noise = null,
                            raw = null,
                            trendArrow = TrendArrow.fromString(reading.getString(KEY_TREND_ARROW)),
                            sourceSensor = SourceSensor.EVERSENSE,
                        )
                    )
                }
            }
        }
    }
}

object EversenseInbox : Inbox<Bundle>("byoesa-bg", EversensePlugin.EversenseWorker::class.java)
