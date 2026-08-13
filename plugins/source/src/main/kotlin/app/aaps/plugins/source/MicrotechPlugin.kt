package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 微泰动泰 / Linx (Microtech Aidex) 血糖来源插件
 * 接收微泰官方 App 广播(com.microtechmd.cgms.aidex.action.BgEstimate)
 * 接口协议参考 xDrip AidexReceiver(通知/广播常量与字段名一致)
 */
@Singleton
class MicrotechPlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_sensor)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.microtech_aidex)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_microtech_aidex),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences
), BgSource {

    // cannot be inner class because of needed injection
    class MicrotechWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var microtechPlugin: MicrotechPlugin
        @Inject lateinit var persistenceLayer: PersistenceLayer

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!microtechPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))

            val timestamp = inputData.getLong("com.microtechmd.cgms.aidex.Time", 0)
            val bgValue = inputData.getDouble("com.microtechmd.cgms.aidex.BgValue", 0.0)
            val bgType = inputData.getString("com.microtechmd.cgms.aidex.BgType")

            if (timestamp <= 0 || bgValue <= 0) return Result.success(workDataOf("Result" to "Invalid data"))

            // 单位换算:mmol/l -> mg/dl(与 xDrip AidexReceiver 一致)
            val bgMgDl = if ("mmol/l".equals(bgType, ignoreCase = true)) {
                (bgValue * 18.0).toInt()
            } else {
                bgValue.toInt()
            }
            if (bgMgDl <= 0) return Result.success(workDataOf("Result" to "Invalid bg value"))

            val glucoseValues = mutableListOf<GV>()
            glucoseValues += GV(
                timestamp = timestamp,
                value = bgMgDl.toDouble(),
                raw = null,
                noise = null,
                trendArrow = TrendArrow.NONE,
                sourceSensor = SourceSensor.AIDEX
            )
            persistenceLayer.insertCgmSourceData(Sources.Aidex, glucoseValues, emptyList(), null)
                .doOnError { ret = Result.failure(workDataOf("Error" to it.toString())) }
                .blockingGet()
            return ret
        }
    }
}
