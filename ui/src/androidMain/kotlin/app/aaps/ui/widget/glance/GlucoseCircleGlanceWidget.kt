package app.aaps.ui.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.unit.ColorProvider
import app.aaps.core.interfaces.configuration.awaitInitialized

/** Home screen widget with the overview BG circle, rendered for each widget size ([SizeMode.Exact]). */
class GlucoseCircleGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = WidgetDependencies.from(context)
        // See AapsGlanceWidget: `done` alone is true during a settings import, awaitInitialized is not.
        val ready = deps.config.awaitInitialized(AWAIT_INIT_TIMEOUT_MS)
        if (!ready) {
            provideContent { Box(modifier = GlanceModifier.fillMaxSize()) {} }
            return
        }
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = deps.widgetStateLoader.loadState(appWidgetId)
        provideContent { GlucoseCircleContent(state) }
    }

    private companion object {

        const val AWAIT_INIT_TIMEOUT_MS = 5_000L
    }
}

@Composable
private fun GlucoseCircleContent(state: WidgetRenderState) {
    val context = LocalContext.current
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val sidePx = (minOf(size.width.value, size.height.value) * density).toInt().coerceAtLeast(1)
    val input = GlucoseCircleInput(
        bgText = state.bgText,
        bgColor = state.bgColor,
        strikeThrough = state.strikeThrough,
        trendArrow = state.trendArrow,
        hasBg = state.arrowResId != null,
        deltaText = state.deltaText,
        timeAgoText = state.timeAgoText
    )
    val bitmap = remember(input, sidePx) { GlucoseCircleBitmapRenderer().render(sidePx, input) }
    // The bitmap has no text for screen readers.
    val description = listOfNotNull(state.bgSpoken, state.arrowDescription, state.deltaText, state.timeAgoText).joinToString(", ")

    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Color(state.backgroundColor)))
        .let { if (launchIntent != null) it.clickable(actionStartActivity(launchIntent)) else it }
    Box(modifier = rootModifier, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.fillMaxSize()
        )
    }
}
