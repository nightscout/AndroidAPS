package app.aaps.ui.widget.glance

import android.content.Context
import androidx.annotation.DrawableRes
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.ui.compose.DarkGeneralColors
import app.aaps.core.ui.compose.navigation.DarkElementColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class AapsGlanceWidget(
    private val stateLoader: WidgetStateLoader,
    private val config: Config
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // When the OS delivers APPWIDGET_UPDATE while the app is still booting
        // (typical on device reboot or cold-start via broadcast), wait briefly
        // for the init flow to complete so the widget doesn't get stuck on
        // LoadingContent until the next refresh tick.
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
            WidgetContent(state)
        }
    }

    private companion object {

        const val AWAIT_INIT_TIMEOUT_MS = 5_000L
    }
}

private val ChipCorner = 8.dp
private val ChipHeight = 40.dp
private val ChipIconSize = 24.dp
private val ChipGap = 3.dp
private val ChipTextSize = 19.sp
private val TextMuted = Color(WidgetTextMuted)

@Composable
private fun WidgetContent(state: WidgetRenderState) {
    val context = LocalContext.current
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val root = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Color(state.backgroundColor)))
        .padding(6.dp)
        .let { if (launchIntent != null) it.clickable(actionStartActivity(launchIntent)) else it }

    Row(modifier = root, verticalAlignment = Alignment.CenterVertically) {
        BgPanel(state, modifier = GlanceModifier.width(125.dp).fillMaxHeight())
        Spacer(modifier = GlanceModifier.width(6.dp))
        ChipsPanel(state, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
    }
}

@Composable
private fun BgPanel(state: WidgetRenderState, modifier: GlanceModifier) {
    val bgColor = Color(state.bgColor)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = state.deltaText,
                style = TextStyle(
                    color = ColorProvider(TextMuted),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
        }
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.bgText,
                    style = TextStyle(
                        color = ColorProvider(bgColor),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (state.strikeThrough) TextDecoration.LineThrough else TextDecoration.None
                    )
                )
                if (state.arrowResId != null) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Image(
                        provider = ImageProvider(state.arrowResId),
                        contentDescription = null,
                        modifier = GlanceModifier.size(30.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(bgColor))
                    )
                }
            }
        }
        Spacer(modifier = GlanceModifier.height(ChipGap))
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.timeAgoText,
                style = TextStyle(
                    color = ColorProvider(TextMuted),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(ChipGap))
        Chip(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            text = state.sensitivityText,
            iconResId = state.sensitivityIconResId,
            accentColor = DarkElementColors.sensitivity,
            isActive = true,
            backgroundAlpha = 0.15f
        )
    }
}

@Composable
private fun ChipsPanel(state: WidgetRenderState, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        Chip(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            text = state.runningModeText,
            iconResId = state.runningModeIconResId,
            accentColor = Color(state.runningModeColor),
            isActive = state.runningModeActive
        )
        if (state.profileText.isNotBlank()) {
            Spacer(modifier = GlanceModifier.height(ChipGap))
            Chip(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                text = state.profileText,
                iconResId = state.profileIconResId,
                accentColor = if (state.profileModified) DarkGeneralColors.inProgress else TextMuted,
                isActive = state.profileModified,
                colorText = state.profileModified
            )
        }
        Spacer(modifier = GlanceModifier.height(ChipGap))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (state.tempTargetText.isNotBlank()) {
                Chip(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    text = state.tempTargetText,
                    iconResId = state.tempTargetIconResId,
                    accentColor = Color(state.tempTargetColor),
                    isActive = state.tempTargetActive
                )
                Spacer(modifier = GlanceModifier.width(ChipGap))
            } else {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
            TbrChip(
                modifier = GlanceModifier.fillMaxHeight(),
                iconResId = state.tbrIconResId
            )
        }
        Spacer(modifier = GlanceModifier.height(ChipGap))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Chip(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                text = state.iobText,
                iconResId = state.iobIconResId,
                accentColor = DarkElementColors.insulin,
                isActive = state.iobActive
            )
            Spacer(modifier = GlanceModifier.width(ChipGap))
            Chip(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                text = state.cobText,
                iconResId = state.cobIconResId,
                accentColor = DarkElementColors.cob,
                isActive = state.cobActive,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun Chip(
    modifier: GlanceModifier,
    text: String,
    @DrawableRes iconResId: Int,
    accentColor: Color,
    isActive: Boolean,
    colorText: Boolean = false,
    maxLines: Int = 1,
    backgroundAlpha: Float = 0.2f
) {
    val background = if (isActive) accentColor.copy(alpha = backgroundAlpha) else Color.Transparent
    val textColor = if (colorText) accentColor else TextMuted
    Box(
        modifier = modifier
            .cornerRadius(ChipCorner)
            .background(ColorProvider(background))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(iconResId),
                contentDescription = null,
                modifier = GlanceModifier.size(ChipIconSize),
                colorFilter = ColorFilter.tint(ColorProvider(accentColor))
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = text,
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontSize = ChipTextSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start
                ),
                maxLines = maxLines
            )
        }
    }
}

@Composable
private fun TbrChip(
    modifier: GlanceModifier,
    @DrawableRes iconResId: Int
) {
    val accentColor = DarkElementColors.tempBasal
    Box(
        modifier = modifier
            .cornerRadius(ChipCorner)
            .background(ColorProvider(accentColor.copy(alpha = 0.2f)))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconResId),
            contentDescription = null,
            modifier = GlanceModifier.size(ChipIconSize),
            colorFilter = ColorFilter.tint(ColorProvider(accentColor))
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
