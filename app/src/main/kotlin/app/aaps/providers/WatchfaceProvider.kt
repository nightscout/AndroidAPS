package app.aaps.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.objects.extensions.round
import dagger.android.DaggerContentProvider
import javax.inject.Inject

/**
 * W527 糖表盘数据 Provider(标准 ContentProvider,无后台广播限制):
 * 表盘 App 定时 query content://info.nightscout.androidaps.watchface/data
 * 返回当前血糖/趋势/IOB/基础率/3h 历史。
 */
class WatchfaceProvider : DaggerContentProvider() {

    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var profileUtil: ProfileUtil

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val lastBg = iobCobCalculator.ads.lastBg() ?: return null
        return try {
            val glucoseStatus = glucoseStatusProvider.getGlucoseStatusData(true)
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            val history = iobCobCalculator.ads.getBucketedDataTableCopy()
                ?.takeLast(36)?.map { it.recalculated }?.toDoubleArray() ?: DoubleArray(0)
            val basal = Regex("\\d+\\.?\\d*").find(overviewData.temporaryBasalText())
                ?.value?.toDoubleOrNull() ?: 0.0

            val c = MatrixCursor(
                arrayOf("bg", "bg_display", "delta", "iob", "basal", "ts", "history")
            )
            c.addRow(
                arrayOf(
                    lastBg.recalculated,
                    profileUtil.fromMgdlToStringInUnits(lastBg.recalculated),
                    glucoseStatus?.delta ?: 0.0,
                    bolusIob.iob + basalIob.iob,
                    basal,
                    lastBg.timestamp,
                    history.joinToString(",")  // Cursor 不支持数组, 逗号分隔字符串
                )
            )
            c
        } catch (e: Exception) {
            null
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.aaps.glance"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
