package app.aaps.ui.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.aaps.core.interfaces.configuration.Config
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class BgGraphGlanceWidget(
    private val stateLoader: BgGraphStateLoader,
    private val config: Config
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ready = config.appInitialized || withTimeoutOrNull(AWAIT_INIT_TIMEOUT_MS) {
            config.initProgressFlow.first { it.done }
        } != null
        if (!ready) {
            provideContent { LoadingContent() }
            return
        }
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = stateLoader.loadState(appWidgetId)
        provideContent {
            BgGraphContent(state)
        }
    }

    private companion object {

        const val AWAIT_INIT_TIMEOUT_MS = 5_000L
    }
}

@Composable
private fun BgGraphContent(state: BgGraphRenderState) {
    val context = LocalContext.current
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val widthPx = (size.width.value * density).toInt().coerceAtLeast(1)
    val heightPx = (size.height.value * density).toInt().coerceAtLeast(1)
    val bitmap = remember(state, widthPx, heightPx) {
        BgGraphBitmapRenderer().render(widthPx, heightPx, state.input, state.colors)
    }
    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Color(state.backgroundColor)))
        .let { if (launchIntent != null) it.clickable(actionStartActivity(launchIntent)) else it }
    Box(modifier = rootModifier, contentAlignment = Alignment.TopCenter) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.bgText,
                style = TextStyle(
                    color = ColorProvider(Color(state.bgColor)),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (state.strikeThrough) TextDecoration.LineThrough else TextDecoration.None
                )
            )
            if (state.arrowResId != null) {
                Spacer(modifier = GlanceModifier.width(3.dp))
                Image(
                    provider = ImageProvider(state.arrowResId),
                    contentDescription = null,
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(Color(state.bgColor)))
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color.Black)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "",
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp)
        )
    }
}
