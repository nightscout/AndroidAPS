package app.aaps.ui.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.ui.compose.navigation.DarkElementColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Compact single-row widget: BG + trend arrow | bolus-icon IOB | carbs-icon COB.
 * Icons act as section dividers — no explicit vertical separators.
 */
class CompactBgGlanceWidget(
    private val stateLoader: WidgetStateLoader,
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
        provideContent { CompactContent(state) }
    }

    private companion object {

        const val AWAIT_INIT_TIMEOUT_MS = 5_000L
    }
}

private val TextMuted = Color(WidgetTextMuted)

@Composable
private fun CompactContent(state: WidgetRenderState) {
    val context = LocalContext.current
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val bgColor = Color(state.bgColor)

    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Color(state.backgroundColor)))
        .padding(horizontal = 4.dp)
        .let { if (launchIntent != null) it.clickable(actionStartActivity(launchIntent)) else it }

    Row(
        modifier = rootModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = state.bgText,
            style = TextStyle(
                color = ColorProvider(bgColor),
                fontSize = TEXT_SIZE,
                fontWeight = FontWeight.Bold,
                textDecoration = if (state.strikeThrough) TextDecoration.LineThrough else TextDecoration.None
            )
        )
        if (state.arrowResId != null) {
            Image(
                provider = ImageProvider(state.arrowResId),
                contentDescription = null,
                modifier = GlanceModifier.size(ICON_SIZE).padding(start = 3.dp),
                colorFilter = ColorFilter.tint(ColorProvider(bgColor))
            )
        }
        Image(
            provider = ImageProvider(state.iobIconResId),
            contentDescription = null,
            modifier = GlanceModifier.size(ICON_SIZE).padding(start = SECTION_GAP),
            colorFilter = ColorFilter.tint(ColorProvider(DarkElementColors.insulin))
        )
        Text(
            text = state.iobText,
            style = TextStyle(
                color = ColorProvider(if (state.iobActive) DarkElementColors.insulin else TextMuted),
                fontSize = TEXT_SIZE
            ),
            modifier = GlanceModifier.padding(start = ICON_TEXT_GAP)
        )
        Image(
            provider = ImageProvider(state.cobIconResId),
            contentDescription = null,
            modifier = GlanceModifier.size(ICON_SIZE).padding(start = SECTION_GAP),
            colorFilter = ColorFilter.tint(ColorProvider(DarkElementColors.cob))
        )
        Text(
            text = state.cobText,
            style = TextStyle(
                color = ColorProvider(if (state.cobActive) DarkElementColors.cob else TextMuted),
                fontSize = TEXT_SIZE
            ),
            modifier = GlanceModifier.padding(start = ICON_TEXT_GAP),
            maxLines = 1
        )
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

private val SECTION_GAP = 6.dp
private val ICON_TEXT_GAP = 3.dp
private val ICON_SIZE = 20.dp
private val TEXT_SIZE = 22.sp
