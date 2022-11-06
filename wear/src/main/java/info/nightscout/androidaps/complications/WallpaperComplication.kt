@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.complications

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.support.wearable.complications.ComplicationData
import android.util.DisplayMetrics
import android.view.WindowManager
import info.nightscout.androidaps.data.RawDisplayData
import info.nightscout.rx.logging.LTag

import java.io.IOException

/*
 * Created by dlvoy on 2019-11-12
 */
abstract class WallpaperComplication : BaseComplicationProviderService() {

    abstract val wallpaperAssetsFileName: String
    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        if (dataType == ComplicationData.TYPE_LARGE_IMAGE) {
            val metrics = DisplayMetrics()
            val windowManager = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager.defaultDisplay.getMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val builder = ComplicationData.Builder(ComplicationData.TYPE_LARGE_IMAGE)
            val assetManager = assets
            try {
                assetManager.open(wallpaperAssetsFileName).use { iStr ->
                    val bitmap = BitmapFactory.decodeStream(iStr)
                    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    builder.setLargeImage(Icon.createWithBitmap(scaled))
                }
            } catch (e: IOException) {
                aapsLogger.error(LTag.WEAR, "Cannot read wallpaper asset: " + e.message, e)
            }
            complicationData = builder.build()
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        return complicationData
    }
}