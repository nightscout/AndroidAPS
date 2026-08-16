package app.aaps.receivers

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.extensions.round
import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject

/**
 * W527 糖表盘数据请求接收器:
 * 表盘 App(com.dana.glance)定时发 app.aaps.watchface.REQUEST → 本接收器响应,
 * 读取最新血糖/IOB/基础率/3h历史, 回发 app.aaps.watchface.BG 广播。
 * 不依赖 UI 生命周期(AAPS 后台/表盘前台均可用)。
 */
class WatchfaceReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REQUEST) return
        try {
            val lastBg = iobCobCalculator.ads.lastBg() ?: return
            val glucoseStatus = glucoseStatusProvider.getGlucoseStatusData(true)
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            val history = iobCobCalculator.ads.getBucketedDataTableCopy()
                ?.takeLast(36)?.map { it.recalculated }?.toDoubleArray() ?: DoubleArray(0)
            val basal = Regex("\\d+\\.?\\d*").find(overviewData.temporaryBasalText())
                ?.value?.toDoubleOrNull() ?: 0.0

            context.sendBroadcast(
                Intent(ACTION_BG).apply {
                    putExtra("bg_display", profileUtil.fromMgdlToStringInUnits(lastBg.recalculated))
                    putExtra("bg", lastBg.recalculated)
                    putExtra("delta", glucoseStatus?.delta ?: 0.0)
                    putExtra("iob", bolusIob.iob + basalIob.iob)
                    putExtra("basal", basal)
                    putExtra("ts", lastBg.timestamp)
                    putExtra("history", history)
                }
            )
            aapsLogger.debug(LTag.CORE, "watchface BG sent: ${lastBg.recalculated}")
        } catch (e: Exception) {
            aapsLogger.debug(LTag.CORE, "watchface request err: ${e.message}")
        }
    }

    companion object {
        const val ACTION_REQUEST = "app.aaps.watchface.REQUEST"
        const val ACTION_BG = "app.aaps.watchface.BG"
    }
}
