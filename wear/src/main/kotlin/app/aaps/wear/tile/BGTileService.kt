package app.aaps.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.wear.data.ComplicationData
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.interaction.menus.MainMenuActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class BGTileService : TileService() {

    @Inject lateinit var complicationDataRepository: ComplicationDataRepository

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val resourceVersion = AtomicLong(System.currentTimeMillis())

    override fun onCreate() {
        injectMetroMembers(this)
        super.onCreate()

        // Re-render and request tile update on each new data arrival
        serviceScope.launch {
            try {
                complicationDataRepository.complicationData
                    .drop(1)
                    .collect {
                        resourceVersion.set(System.currentTimeMillis())
                        getUpdater(this@BGTileService)
                            .requestUpdate(BGTileService::class.java)
                    }
            } catch (ignored: Exception) {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> = serviceScope.future {
        val data = try {
            complicationDataRepository.complicationData.first()
        } catch (e: Exception) {
            ComplicationData()
        }
        val bgData = data.bgData
        val statusData = data.statusData

        val sgvText = bgData.sgvString.ifEmpty { "---" }
        val arrowText = bgData.slopeArrow.ifEmpty { "--" }
        val deltaText = bgData.delta.ifEmpty { "--" }
        val iobText = "IOB: ${statusData.iobSum.ifEmpty { "0" }}"
        val cobText = "COB: ${statusData.cob.ifEmpty { "0" }}"

        val minutesAgo = if (bgData.timeStamp > 0) {
            val diffMs = System.currentTimeMillis() - bgData.timeStamp
            val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            "$mins min ago"
        } else {
            "--"
        }

        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainMenuActivity::class.java.name)
                    .build()
            )
            .build()

        val clickable = Clickable.Builder()
            .setOnClick(launchAction)
            .build()

        val layout = Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setClickable(clickable)
                    .build()
            )
            .addContent(
                Text.Builder()
                    .setText(sgvText)
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(sp(60f))
                            .setWeight(FONT_WEIGHT_BOLD)
                            .setColor(argb(getBgColor(bgData.sgvLevel.toInt())))
                            .build()
                    )
                    .build()
            )
            .addContent(Spacer.Builder().setHeight(dp(2f)).build())
            .addContent(
                Row.Builder()
                    .addContent(
                        Text.Builder()
                            .setText(arrowText)
                            .setFontStyle(FontStyle.Builder().setSize(sp(24f)).build())
                            .build()
                    )
                    .addContent(Spacer.Builder().setWidth(dp(10f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(deltaText)
                            .setFontStyle(FontStyle.Builder().setSize(sp(24f)).build())
                            .build()
                    )
                    .build()
            )
            .addContent(Spacer.Builder().setHeight(dp(10f)).build())
            .addContent(
                Row.Builder()
                    .addContent(
                        Text.Builder()
                            .setText(iobText)
                            .setFontStyle(FontStyle.Builder().setSize(sp(16f)).build())
                            .build()
                    )
                    .addContent(Spacer.Builder().setWidth(dp(12f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(cobText)
                            .setFontStyle(FontStyle.Builder().setSize(sp(16f)).build())
                            .build()
                    )
                    .build()
            )
            .addContent(Spacer.Builder().setHeight(dp(12f)).build())
            .addContent(
                Text.Builder()
                    .setText(minutesAgo)
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(sp(14f))
                            .setColor(argb(0xFFB0BEC5.toInt()))
                            .build()
                    )
                    .build()
            )
            .build()

        Tile.Builder()
            .setResourcesVersion(resourceVersion.get().toString())
            .setTileTimeline(Timeline.fromLayoutElement(layout))
            .setFreshnessIntervalMillis(60 * 1000)
            .build()
    }

    @Deprecated("Deprecated in TileService but still required for now")
    override fun onResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(resourceVersion.get().toString())
                .build()
        )

    private fun getBgColor(level: Int): Int = when (level) {
        -1   -> 0xFFFF5252.toInt() // Red
        1    -> 0xFFFFD600.toInt()  // Yellow
        else -> 0xFF81C784.toInt() // Green
    }
}
