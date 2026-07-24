package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.interaction.activities.renderBgGraph
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import app.aaps.wear.data.ComplicationData as ComplicationStore
import javax.inject.Inject
import kotlin.math.sin

/**
 * BG Graph Complication
 *
 * Renders the BG history graph (same renderer as the BG graph tile and `BgGraphActivity`) as an
 * image complication, so it can be placed inside any watchface with an image slot — most notably
 * Watch Face Format (WFF) faces on watches that no longer support code-based watchfaces.
 * Types: SMALL_IMAGE (small slots) and PHOTO_IMAGE (large slots)
 */
class BgGraphComplication : ModernBaseComplicationProviderService() {

    @Inject lateinit var sp: SP

    override fun buildComplicationData(
        type: ComplicationType,
        data: ComplicationStore,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        val contentDescription = PlainComplicationText.Builder(text = getString(R.string.complication_bg_graph)).build()

        return when (type) {
            ComplicationType.SMALL_IMAGE -> {
                val icon = Icon.createWithBitmap(renderGraphBitmap(data))
                SmallImageComplicationData.Builder(
                    smallImage = SmallImage.Builder(image = icon, type = SmallImageType.PHOTO).build(),
                    contentDescription = contentDescription
                )
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            ComplicationType.PHOTO_IMAGE -> {
                val icon = Icon.createWithBitmap(renderGraphBitmap(data))
                PhotoImageComplicationData.Builder(
                    photoImage = icon,
                    contentDescription = contentDescription
                )
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                         -> {
                aapsLogger.warn(LTag.WEAR, "BgGraphComplication unexpected type: $type")
                null
            }
        }
    }

    // Rendered at screen width and 2:1 so the dp-based sizes inside renderBgGraph (hour labels,
    // bottom reserve, line widths) keep the same proportions as on the tile/activity; the face
    // downscales the bitmap to its slot.
    // No background fill — the watchface behind the slot supplies it, so the graph blends into
    // any (dark) face instead of showing a hard black rectangle. The now label is dropped: the
    // now-line plus hour labels already anchor the time axis, and the face shows the clock itself
    private fun renderGraphBitmap(data: ComplicationStore): Bitmap {
        val widthPx = resources.displayMetrics.widthPixels
        val heightPx = widthPx / 2
        val bitmap = createBitmap(widthPx, heightPx)
        val historyHours = sp.getString("complication_bg_graph_hours", "3").toIntOrNull() ?: 3
        CanvasDrawScope().draw(
            density = Density(resources.displayMetrics.density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = ComposeCanvas(Canvas(bitmap)),
            size = Size(widthPx.toFloat(), heightPx.toFloat())
        ) {
            renderBgGraph(data, historyHours, showNowLabel = false)
        }
        return bitmap
    }

    // The default preview has no graph history — synthesize a plausible BG curve so the
    // watchface editor / complication picker shows a real-looking graph
    override fun getPreviewComplicationData(): ComplicationStore {
        val base = super.getPreviewComplicationData()
        val now = System.currentTimeMillis()
        val entries = ArrayList<EventData.SingleBg>()
        for (i in 0 until 36) {
            val sgv = 130.0 + 45.0 * sin(i / 6.0)
            entries.add(
                EventData.SingleBg(
                    dataset = 0,
                    timeStamp = now - (35 - i) * 5 * 60_000L,
                    sgvLevel = if (sgv > 180.0) 1L else if (sgv < 70.0) -1L else 0L,
                    sgv = sgv,
                    high = 180.0,
                    low = 70.0
                )
            )
        }
        return base.copy(graphData = EventData.GraphData(entries))
    }

    override fun getComplicationAction(): ComplicationAction = ComplicationAction.BG_GRAPH

    override fun getProviderCanonicalName(): String = BgGraphComplication::class.java.canonicalName!!
}
