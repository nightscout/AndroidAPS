package app.aaps.wear.tile

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import app.aaps.wear.R
import app.aaps.wear.data.ComplicationData
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.interaction.activities.BgGraphActivity
import app.aaps.wear.interaction.activities.formatTtDuration
import app.aaps.wear.interaction.activities.renderBgGraph
import app.aaps.wear.interaction.utils.DisplayFormat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.android.AndroidInjection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation

class BgGraphTileService : TileService() {

    @Inject lateinit var complicationDataRepository: ComplicationDataRepository
    @Inject lateinit var displayFormat: DisplayFormat

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val resourceVersion = AtomicLong(System.currentTimeMillis())

    // Pre-rendered bitmap bytes — populated in onCreate before system asks for resources
    private val cachedBytes = AtomicReference<ByteArray?>(null)
    private val initialRender = CompletableDeferred<ByteArray>()

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()

        // Render current data immediately so onResourcesRequest finds a warm cache
        serviceScope.launch {
            try {
                val data = complicationDataRepository.complicationData.first()
                renderAndCache(data)
            } catch (e: Exception) {
                if (!initialRender.isCompleted) initialRender.completeExceptionally(e)
            }
        }

        // Re-render and request tile update on each new data arrival
        serviceScope.launch {
            complicationDataRepository.complicationData
                .drop(1)
                .collect { data ->
                    renderAndCache(data)
                    resourceVersion.set(System.currentTimeMillis())
                    getUpdater(this@BgGraphTileService)
                        .requestUpdate(BgGraphTileService::class.java)
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    // No async work needed — returns immediately
    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        Futures.immediateFuture(
            Tile.Builder()
                .setResourcesVersion(resourceVersion.get().toString())
                .setTileTimeline(Timeline.fromLayoutElement(buildLayout()))
                .build()
        )

    @Deprecated("Deprecated in TileService but still required for now")
    override fun onResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        serviceScope.future {
            val bytes = cachedBytes.get()
                ?: withTimeoutOrNull(5_000) { initialRender.await() }
                ?: placeholderBytes()

            val cfg = requestParams.deviceConfiguration
            val density = resources.displayMetrics.density
            val widthPx = (cfg.screenWidthDp * density).toInt()
            val heightPx = (cfg.screenHeightDp * density).toInt()

            ResourceBuilders.Resources.Builder()
                .setVersion(resourceVersion.get().toString())
                .addIdToImageMapping(
                    IMAGE_ID,
                    ResourceBuilders.ImageResource.Builder()
                        .setInlineResource(
                            ResourceBuilders.InlineImageResource.Builder()
                                .setData(bytes)
                                .setWidthPx(widthPx)
                                .setHeightPx(heightPx)
                                .build()
                        )
                        .build()
                )
                .build()
        }

    private fun placeholderBytes(): ByteArray {
        val widthPx = resources.displayMetrics.widthPixels
        val heightPx = resources.displayMetrics.heightPixels
        val bitmap = createBitmap(widthPx, heightPx)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    private fun renderAndCache(data: ComplicationData) {
        val widthPx = resources.displayMetrics.widthPixels
        val heightPx = resources.displayMetrics.heightPixels
        val bitmap = renderTileBitmap(data, widthPx, heightPx)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        val bytes = stream.toByteArray()
        cachedBytes.set(bytes)
        if (!initialRender.isCompleted) initialRender.complete(bytes)
    }

    private fun buildLayout(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open_bg_graph")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(BgGraphActivity::class.java.name)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(IMAGE_ID)
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .build()
            )
            .build()

    private fun renderTileBitmap(data: ComplicationData, widthPx: Int, heightPx: Int): Bitmap {
        val bitmap = createBitmap(widthPx, heightPx)
        val androidCanvas = android.graphics.Canvas(bitmap)
        androidCanvas.drawColor(android.graphics.Color.BLACK)

        val headerHeightPx = renderHeader(androidCanvas, data, widthPx)
        val graphHeightPx = heightPx - headerHeightPx

        val composeCanvas = Canvas(androidCanvas)
        val drawScope = CanvasDrawScope()
        androidCanvas.withTranslation(0f, headerHeightPx.toFloat()) {
            drawScope.draw(
                density = Density(resources.displayMetrics.density),
                layoutDirection = LayoutDirection.Ltr,
                canvas = composeCanvas,
                size = Size(widthPx.toFloat(), graphHeightPx.toFloat())
            ) {
                renderBgGraph(data, 3)
            }
        }

        return bitmap
    }

    private fun renderHeader(canvas: android.graphics.Canvas, data: ComplicationData, widthPx: Int): Int {
        val density = resources.displayMetrics.density
        val bgData = data.bgData
        val statusData = data.statusData
        val now = System.currentTimeMillis()
        val ageMs = now - bgData.timeStamp
        val ageMin = ageMs / 60_000
        val hourUnit = getString(R.string.hour_short)
        val minUnit = getString(R.string.minute_short)
        val insulinUnit = getString(R.string.insulin_unit_short)

        val hasTT = statusData.tempTargetDuration >= 0
        val targetText = if (hasTT) "${statusData.tempTarget} (${formatTtDuration(statusData.tempTargetDuration, hourUnit)})"
                         else statusData.tempTarget
        val basalText = displayFormat.basalRateSymbol().trimEnd() + statusData.currentBasal.replaceFirst(" ", "")

        val bgSizePx = 34 * density
        val smSizePx = 12 * density
        val combinedLen = "${statusData.iobSum}$insulinUnit".length + statusData.cob.length +
            basalText.length + targetText.length
        val statSizePx = if (combinedLen > 30) 11 * density else 12 * density

        val bgArgb = when (bgData.sgvLevel.toInt()) {
            -1   -> android.graphics.Color.rgb(255, 0, 0)
            1    -> android.graphics.Color.rgb(255, 255, 0)
            else -> android.graphics.Color.rgb(0, 255, 0)
        }
        val ageArgb = when {
            ageMs / 60_000 < 4  -> android.graphics.Color.rgb(0, 255, 0)
            ageMs / 60_000 < 10 -> android.graphics.Color.rgb(255, 152, 0)
            else                -> android.graphics.Color.rgb(255, 0, 0)
        }
        val secondaryArgb = android.graphics.Color.rgb(170, 170, 170)
        val iobArgb   = android.graphics.Color.rgb(30, 136, 229)
        val cobArgb   = android.graphics.Color.rgb(255, 109, 0)
        val basalArgb = android.graphics.Color.rgb(144, 202, 249)
        val targetArgb = when (statusData.tempTargetLevel) {
            1    -> android.graphics.Color.rgb(119, 221, 119)
            2    -> android.graphics.Color.rgb(253, 216, 53)
            else -> secondaryArgb
        }

        val spacerPx = 16 * density
        val bgPaint = Paint().apply {
            color = bgArgb
            textSize = bgSizePx
            textAlign = Paint.Align.LEFT
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val smPaint = Paint().apply {
            color = secondaryArgb
            textSize = smSizePx
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }
        val agePaint = Paint().apply {
            color = ageArgb
            textSize = smSizePx
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }

        val arrowSizePx = 26 * density
        val arrowPaint = Paint().apply {
            color = bgArgb
            textSize = arrowSizePx
            textAlign = Paint.Align.LEFT
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val bgValueText = bgData.sgvString
        val arrowText = "${bgData.slopeArrow}\uFE0E"
        val valueWidth = bgPaint.measureText(bgValueText)
        val arrowWidth = arrowPaint.measureText(arrowText)
        val bgTextWidth = valueWidth + arrowWidth
        val gapPx = 6 * density
        val totalRowW = bgTextWidth + gapPx + smPaint.measureText(bgData.delta)
        val rowStartX = (widthPx - totalRowW) / 2f
        val bgBaseY = spacerPx + bgSizePx

        canvas.drawText(bgValueText, rowStartX, bgBaseY, bgPaint)
        canvas.drawText(arrowText, rowStartX + valueWidth, bgBaseY - (bgSizePx - arrowSizePx) * 0.3f, arrowPaint)
        canvas.drawText(bgData.delta, rowStartX + bgTextWidth + gapPx, bgBaseY - bgSizePx * 0.45f, smPaint)
        canvas.drawText("${ageMin}$minUnit", rowStartX + bgTextWidth + gapPx, bgBaseY - bgSizePx * 0.45f + smSizePx * 1.5f, agePaint)

        val statsY = bgBaseY + smSizePx * 1.2f + 4 * density + statSizePx
        val statGapPx = 8 * density
        val statItems = listOf(
            "${statusData.iobSum}$insulinUnit" to iobArgb,
            statusData.cob                     to cobArgb,
            basalText                          to basalArgb,
            targetText                         to targetArgb
        )
        val statPaints = statItems.map { (_, color) ->
            Paint().apply {
                this.color = color
                textSize = statSizePx
                textAlign = Paint.Align.LEFT
                isAntiAlias = true
            }
        }
        val statWidths = statItems.zip(statPaints).map { (item, paint) -> paint.measureText(item.first) }
        val totalStatWidth = statWidths.sum() + statGapPx * (statItems.size - 1)
        var statX = (widthPx - totalStatWidth) / 2f
        statItems.zip(statPaints).forEachIndexed { i, (item, paint) ->
            canvas.drawText(item.first, statX, statsY, paint)
            statX += statWidths[i] + statGapPx
        }

        return (statsY + statSizePx * 0.5f + 6 * density).toInt()
    }

    companion object {
        private const val IMAGE_ID = "bg_graph"
    }
}
