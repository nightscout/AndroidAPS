package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.view.WindowManager
import androidx.core.graphics.scale
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import app.aaps.core.interfaces.logging.LTag
import java.io.IOException

/**
 * Wallpaper Complication (Abstract Base)
 *
 * Provides wallpaper image complications scaled to watch screen size
 * Subclasses specify the wallpaper asset file to display
 * Type: LARGE_IMAGE
 *
 */
abstract class WallpaperComplication : ModernBaseComplicationProviderService() {

    abstract val wallpaperAssetsFileName: String

    override fun buildComplicationData(
        type: ComplicationType,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        return when (type) {
            ComplicationType.PHOTO_IMAGE      -> {
                val windowManager = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
                val bounds = windowManager.currentWindowMetrics.bounds
                val width = bounds.width()
                val height = bounds.height()
                val assetManager = assets
                var photoIcon: Icon? = null
                try {
                    assetManager.open(wallpaperAssetsFileName).use { iStr ->
                        val bitmap = BitmapFactory.decodeStream(iStr)
                        val scaled = bitmap.scale(width, height)
                        photoIcon = Icon.createWithBitmap(scaled)
                    }
                } catch (e: IOException) {
                    aapsLogger.error(LTag.WEAR, "Cannot read wallpaper asset: " + e.message, e)
                }
                photoIcon?.let {
                    PhotoImageComplicationData.Builder(
                        photoImage = it,
                        contentDescription = PlainComplicationText.Builder(text = "Wallpaper").build()
                    ).build()
                }
            }

            else                              -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getComplicationAction(): ComplicationAction = ComplicationAction.NONE
}