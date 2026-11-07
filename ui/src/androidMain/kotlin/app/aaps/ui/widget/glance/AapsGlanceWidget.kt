package app.aaps.ui.widget.glance

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
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
import app.aaps.core.interfaces.configuration.awaitInitialized
import app.aaps.core.ui.compose.DarkGeneralColors
import app.aaps.core.ui.compose.navigation.DarkElementColors

class AapsGlanceWidget : GlanceAppWidget() {

    // Exact, so the widget knows its size and can choose font sizes at which the texts fit. This is the
    // size the launcher reports, and it is not always the real one, so the layout fills by weight.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Resolve dependencies from the app graph here (not at construction time).
        // See WidgetDependencies for why.
        val deps = WidgetDependencies.from(context)
        val stateLoader = deps.widgetStateLoader
        val config = deps.config

        // When the OS delivers APPWIDGET_UPDATE while the app is still booting
        // (typical on device reboot or cold-start via broadcast), wait briefly
        // for the init flow to complete so the widget doesn't get stuck on
        // LoadingContent until the next refresh tick.
        // awaitInitialized, not a hand-rolled wait on `done`: during a settings import `done` is
        // already true while plugin state is being rebuilt, so waiting on it alone returns at once and
        // the loader below reads plugin state in exactly the window it means to avoid.
        val ready = config.awaitInitialized(AWAIT_INIT_TIMEOUT_MS)
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

private val OuterPadding = 6.dp
private val PanelGap = 6.dp
private val BgPanelWidth = 125.dp
private val ChipCorner = 8.dp
private val ChipPadding = 8.dp
private val ChipIconSize = 24.dp
private val ChipIconGap = 6.dp
private val ChipGap = 3.dp
private val IsfArrowSize = 16.dp
private val TrendArrowSize = 30.dp
private val TrendArrowGap = 4.dp
private val TextMuted = Color(WidgetTextMuted)
private val ChipNeutral = Color(WidgetChipNeutral)

// Texts use the largest size while they fit and get smaller only when they do not.
private const val ChipMaxSp = 19f
private const val ChipMinSp = 12f // what the 3.x widget used
private const val BgMaxSp = 44f
private const val BgMinSp = 24f
private const val BgSideMaxSp = 20f // delta and time ago, made smaller together with the glucose value
private const val ProfileMaxLines = 2
private const val ChipRows = 3

/** Heights and font sizes for one widget size, chosen so that every text fits. */
private class WidgetSizes(
    val rowHeight: Dp,
    val bgSp: Float,
    val bgSideSp: Float,
    val bgScale: Float,
    val profile: TextFit,
    val tempTargetSp: Float,
    val iobCobSp: Float,
    val sensitivitySp: Float
)

private fun measureSizes(state: WidgetRenderState, size: DpSize, fitter: WidgetTextFitter): WidgetSizes {
    val innerWidth = size.width.value - 2 * OuterPadding.value
    val innerHeight = size.height.value - 2 * OuterPadding.value
    val rowHeight = ((innerHeight - (ChipRows - 1) * ChipGap.value) / ChipRows).coerceAtLeast(1f)
    val rowHeightPx = fitter.dpToPx(rowHeight)
    val chipsWidth = innerWidth - BgPanelWidth.value - PanelGap.value
    // The parts of a chip that are not text: the padding on both sides, the icon and the gap after it.
    val chipFrame = 2 * ChipPadding.value + ChipIconSize.value + ChipIconGap.value
    val iconChipWidth = 2 * ChipPadding.value + ChipIconSize.value

    // Delta, glucose and time ago share the space above the sensitivity chip and get smaller together.
    val bgAreaWidthPx = fitter.widthLimitPx(BgPanelWidth.value)
    val bgAreaHeightPx = fitter.dpToPx(innerHeight - rowHeight - ChipGap.value)
    val bgSp = fitter.largestFitting(BgMaxSp, BgMinSp) { sp ->
        val scale = sp / BgMaxSp
        val sideSp = BgSideMaxSp * scale
        val arrowPx = fitter.dpToPx((TrendArrowGap.value + TrendArrowSize.value) * scale)
        fitter.widthPx(state.bgText, sp) + arrowPx <= bgAreaWidthPx &&
            fitter.widthPx(state.deltaText, sideSp) <= bgAreaWidthPx &&
            fitter.widthPx(state.timeAgoText, sideSp) <= bgAreaWidthPx &&
            fitter.heightPx(sp) + 2 * fitter.heightPx(sideSp) <= bgAreaHeightPx
    }
    val bgScale = bgSp / BgMaxSp

    // The loop icon chip sits beside the profile, so the profile has the rest of the row.
    val profile = fitter.fitWrapping(
        text = state.profileText,
        widthDp = chipsWidth - iconChipWidth - ChipGap.value - chipFrame,
        heightDp = rowHeight,
        maxSp = ChipMaxSp,
        minSp = ChipMinSp,
        maxLines = ProfileMaxLines
    )

    val tempTargetWidthPx = fitter.widthLimitPx(chipsWidth - ChipGap.value - iconChipWidth - chipFrame)
    val tempTargetSp = fitter.largestFitting(ChipMaxSp, ChipMinSp) { sp ->
        fitter.widthPx(state.tempTargetText, sp) <= tempTargetWidthPx && fitter.heightPx(sp) <= rowHeightPx
    }

    // One size for both, so the row looks even. COB takes the width it needs and IOB the rest.
    val iobCobWidthPx = fitter.widthLimitPx(chipsWidth - ChipGap.value - 2 * chipFrame)
    val cobLines = state.cobText.lines().size
    val iobCobSp = fitter.largestFitting(ChipMaxSp, ChipMinSp) { sp ->
        fitter.widthPx(state.iobText, sp) + fitter.widthPx(state.cobText, sp) <= iobCobWidthPx &&
            fitter.heightPx(sp, cobLines) <= rowHeightPx
    }

    val hasIsfStep = state.isfFromText.isNotEmpty()
    val sensitivityFrame = chipFrame +
        (if (hasIsfStep) IsfArrowSize.value else 0f) +
        (if (hasIsfStep && state.sensitivityText.isNotEmpty()) ChipIconGap.value else 0f)
    val sensitivityWidthPx = fitter.widthLimitPx(BgPanelWidth.value - sensitivityFrame)
    val sensitivitySp = fitter.largestFitting(ChipMaxSp, ChipMinSp) { sp ->
        fitter.widthPx(state.sensitivityText, sp) + fitter.widthPx(state.isfFromText, sp) + fitter.widthPx(state.isfToText, sp) <= sensitivityWidthPx &&
            fitter.heightPx(sp) <= rowHeightPx
    }

    return WidgetSizes(
        rowHeight = rowHeight.dp,
        bgSp = bgSp,
        bgSideSp = BgSideMaxSp * bgScale,
        bgScale = bgScale,
        profile = profile,
        tempTargetSp = tempTargetSp,
        iobCobSp = iobCobSp,
        sensitivitySp = sensitivitySp
    )
}

@Composable
private fun WidgetContent(state: WidgetRenderState) {
    val context = LocalContext.current
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val sizes = measureSizes(state, LocalSize.current, WidgetTextFitter(context))
    val root = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Color(state.backgroundColor)))
        .padding(OuterPadding)
        .let { if (launchIntent != null) it.clickable(actionStartActivity(launchIntent)) else it }

    Row(modifier = root) {
        BgPanel(state, sizes, modifier = GlanceModifier.width(BgPanelWidth).fillMaxHeight())
        Spacer(modifier = GlanceModifier.width(PanelGap))
        ChipsPanel(state, sizes, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
    }
}

@Composable
private fun BgPanel(state: WidgetRenderState, sizes: WidgetSizes, modifier: GlanceModifier) {
    val bgColor = Color(state.bgColor)
    Column(modifier = modifier) {
        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.deltaText,
                style = TextStyle(
                    color = ColorProvider(TextMuted),
                    fontSize = sizes.bgSideSp.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.bgText,
                    style = TextStyle(
                        color = ColorProvider(bgColor),
                        fontSize = sizes.bgSp.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (state.strikeThrough) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 1,
                    // Says "old reading" when the value is struck through.
                    modifier = GlanceModifier.semantics { contentDescription = state.bgSpoken }
                )
                if (state.arrowResId != null) {
                    Spacer(modifier = GlanceModifier.width(TrendArrowGap * sizes.bgScale))
                    Image(
                        provider = ImageProvider(state.arrowResId),
                        contentDescription = state.arrowDescription,
                        modifier = GlanceModifier.size(TrendArrowSize * sizes.bgScale),
                        colorFilter = ColorFilter.tint(ColorProvider(bgColor))
                    )
                }
            }
            Text(
                text = state.timeAgoText,
                style = TextStyle(
                    color = ColorProvider(TextMuted),
                    fontSize = sizes.bgSideSp.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.height(ChipGap))
        // Same height as the chip rows on the right, so the bottom row lines up. If the launcher reports a
        // smaller size than the real one, this chip is lower than the rows, but it stays at the bottom.
        SensitivityChip(state, sizes.sensitivitySp, modifier = GlanceModifier.fillMaxWidth().height(sizes.rowHeight))
    }
}

@Composable
private fun ChipsPanel(state: WidgetRenderState, sizes: WidgetSizes, modifier: GlanceModifier) {
    // The rows fill by weight, not with the measured row height. A launcher can report a size that is
    // smaller than the real one (a Galaxy S23+ did), and rows with a fixed height then filled only the
    // top of the widget.
    Column(modifier = modifier) {
        // The loop is an icon only, beside the profile, as on the overview. Its name is spoken.
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            IconChip(
                modifier = GlanceModifier.fillMaxHeight(),
                iconResId = state.runningModeIconResId,
                iconDescription = state.runningModeText,
                accentColor = Color(state.runningModeColor),
                attention = state.runningModeActive
            )
            if (state.profileText.isNotBlank()) {
                Spacer(modifier = GlanceModifier.width(ChipGap))
                Chip(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    text = state.profileText,
                    fontSp = sizes.profile.sp,
                    maxLines = sizes.profile.maxLines,
                    iconResId = state.profileIconResId,
                    accentColor = if (state.profileModified) DarkGeneralColors.inProgress else TextMuted,
                    attention = state.profileModified,
                    colorText = state.profileModified
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(ChipGap))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (state.tempTargetText.isNotBlank()) {
                Chip(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    text = state.tempTargetText,
                    fontSp = sizes.tempTargetSp,
                    iconResId = state.tempTargetIconResId,
                    accentColor = Color(state.tempTargetColor),
                    attention = state.tempTargetActive
                )
                Spacer(modifier = GlanceModifier.width(ChipGap))
            } else {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
            IconChip(
                modifier = GlanceModifier.fillMaxHeight(),
                iconResId = state.tbrIconResId,
                // Spoken state. This chip is icon only, so without it a screen reader gets nothing at all.
                iconDescription = state.tbrDescription,
                accentColor = DarkElementColors.tempBasal,
                attention = false
            )
        }
        Spacer(modifier = GlanceModifier.height(ChipGap))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Chip(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                text = state.iobText,
                fontSp = sizes.iobCobSp,
                iconResId = state.iobIconResId,
                iconDescription = state.iobLabel,
                accentColor = DarkElementColors.insulin,
                attention = false
            )
            Spacer(modifier = GlanceModifier.width(ChipGap))
            Chip(
                modifier = GlanceModifier.fillMaxHeight(),
                text = state.cobText,
                fontSp = sizes.iobCobSp,
                iconResId = state.cobIconResId,
                iconDescription = state.cobLabel,
                accentColor = DarkElementColors.cob,
                attention = state.carbsRequired,
                maxLines = 2
            )
        }
    }
}

/**
 * The same values as the overview sensitivity chip: the autosens percentage, then the step from the
 * profile ISF to the ISF in use. Either part is left out when it is empty.
 */
@Composable
private fun SensitivityChip(state: WidgetRenderState, fontSp: Float, modifier: GlanceModifier) {
    val accentColor = DarkElementColors.sensitivity
    Box(
        modifier = modifier.chipBackground(accentColor, attention = false).padding(horizontal = ChipPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipIcon(state.sensitivityIconResId, iconDescription = null, accentColor)
            if (state.sensitivityText.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.width(ChipIconGap))
                ChipText(state.sensitivityText, fontSp, TextMuted)
            }
            if (state.isfFromText.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.width(ChipIconGap))
                ChipText(state.isfFromText, fontSp, TextMuted)
                Image(
                    provider = ImageProvider(state.isfStepIconResId),
                    contentDescription = state.isfStepDescription,
                    modifier = GlanceModifier.size(IsfArrowSize),
                    colorFilter = ColorFilter.tint(ColorProvider(accentColor))
                )
                ChipText(state.isfToText, fontSp, TextMuted)
            }
        }
    }
}

@Composable
private fun Chip(
    modifier: GlanceModifier,
    text: String,
    fontSp: Float,
    @DrawableRes iconResId: Int,
    accentColor: Color,
    /** Only a chip that asks for attention gets its own colour; every other chip looks the same. */
    attention: Boolean,
    colorText: Boolean = false,
    maxLines: Int = 1,
    /** Spoken name of the icon. The text beside it is a bare value, so the icon carries the noun. */
    iconDescription: String? = null
) {
    Box(
        modifier = modifier.chipBackground(accentColor, attention).padding(horizontal = ChipPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipIcon(iconResId, iconDescription, accentColor)
            Spacer(modifier = GlanceModifier.width(ChipIconGap))
            ChipText(text, fontSp, if (colorText) accentColor else TextMuted, maxLines)
        }
    }
}

@Composable
private fun IconChip(
    modifier: GlanceModifier,
    @DrawableRes iconResId: Int,
    iconDescription: String,
    accentColor: Color,
    attention: Boolean
) {
    Box(
        modifier = modifier.chipBackground(accentColor, attention).padding(horizontal = ChipPadding),
        contentAlignment = Alignment.Center
    ) {
        ChipIcon(iconResId, iconDescription, accentColor)
    }
}

@Composable
private fun ChipIcon(@DrawableRes iconResId: Int, iconDescription: String?, accentColor: Color) {
    Image(
        provider = ImageProvider(iconResId),
        contentDescription = iconDescription,
        modifier = GlanceModifier.size(ChipIconSize),
        colorFilter = ColorFilter.tint(ColorProvider(accentColor))
    )
}

@Composable
private fun ChipText(text: String, fontSp: Float, color: Color, maxLines: Int = 1) {
    Text(
        text = text,
        style = TextStyle(
            color = ColorProvider(color),
            fontSize = fontSp.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        ),
        maxLines = maxLines
    )
}

private fun GlanceModifier.chipBackground(accentColor: Color, attention: Boolean): GlanceModifier =
    cornerRadius(ChipCorner).background(ColorProvider(if (attention) accentColor.copy(alpha = 0.2f) else ChipNeutral))

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
