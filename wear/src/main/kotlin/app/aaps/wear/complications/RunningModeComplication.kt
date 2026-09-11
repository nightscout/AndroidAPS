package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.LoopStatusData
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.LoopClosedColor
import app.aaps.wear.interaction.actions.LoopDisabledColor
import app.aaps.wear.interaction.actions.LoopDisconnectedColor
import app.aaps.wear.interaction.actions.LoopLgsColor
import app.aaps.wear.interaction.actions.LoopOpenColor
import app.aaps.wear.interaction.actions.LoopSuperbolusColor
import app.aaps.wear.interaction.actions.LoopUnknownColor
import app.aaps.wear.data.ComplicationData as ComplicationStore

/**
 * Running Mode Complication
 *
 * Shows an icon of the current running mode (closed loop, open loop, LGS,
 * disabled, suspended, disconnected, superbolus).
 * Tap opens a small picker with the allowed mode changes.
 *
 * Types:
 * - SMALL_IMAGE: full-color icon (one color per mode)
 * - MONOCHROMATIC_IMAGE: icon tinted by the watchface; modes differ by glyph shape
 *
 * Image-only by design: no text and no remaining duration — the loop status screen has those.
 */
class RunningModeComplication : ModernBaseComplicationProviderService() {

    /** Icon, color and accessibility label for one running mode. */
    private data class ModeAppearance(
        @DrawableRes val iconRes: Int,
        val colorArgb: Int,
        @StringRes val fullLabelRes: Int
    )

    private fun LoopStatusData.LoopMode.appearance(): ModeAppearance = when (this) {
        LoopStatusData.LoopMode.CLOSED         -> ModeAppearance(R.drawable.ic_loop_closed_green, LoopClosedColor.toArgb(), R.string.loop_status_closed)
        LoopStatusData.LoopMode.OPEN           -> ModeAppearance(R.drawable.ic_loop_open, LoopOpenColor.toArgb(), R.string.loop_status_open)
        LoopStatusData.LoopMode.LGS            -> ModeAppearance(R.drawable.ic_loop_lgs, LoopLgsColor.toArgb(), R.string.loop_status_lgs)
        LoopStatusData.LoopMode.DISABLED       -> ModeAppearance(R.drawable.ic_loop_disabled, LoopDisabledColor.toArgb(), R.string.loop_status_disabled)
        // Icon red like ic_loop_paused (LoopSuspendedColor yellow is the suspend TEXT color, not the icon color)
        LoopStatusData.LoopMode.SUSPENDED      -> ModeAppearance(R.drawable.ic_loop_paused, LoopDisabledColor.toArgb(), R.string.loop_status_suspended)
        LoopStatusData.LoopMode.PUMP_SUSPENDED -> ModeAppearance(R.drawable.ic_loop_paused, LoopDisabledColor.toArgb(), R.string.loop_status_pump_suspended)
        LoopStatusData.LoopMode.DST_SUSPENDED  -> ModeAppearance(R.drawable.ic_loop_paused, LoopDisabledColor.toArgb(), R.string.loop_status_dst_suspended)
        LoopStatusData.LoopMode.DISCONNECTED   -> ModeAppearance(R.drawable.ic_loop_disconnected, LoopDisconnectedColor.toArgb(), R.string.loop_status_disconnected)
        LoopStatusData.LoopMode.SUPERBOLUS     -> ModeAppearance(R.drawable.ic_bolus, LoopSuperbolusColor.toArgb(), R.string.loop_status_superbolus)
        LoopStatusData.LoopMode.UNKNOWN        -> ModeAppearance(R.drawable.ic_loop_closed_green, LoopUnknownColor.toArgb(), R.string.loop_status_unknown)
    }

    override fun buildComplicationData(
        type: ComplicationType,
        data: ComplicationStore,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? = buildForMode(type, data.statusData.loopMode, complicationPendingIntent)

    /**
     * No fresh data from the phone — the real mode is not known anymore.
     * Show UNKNOWN instead of a possibly wrong last mode.
     */
    override fun buildNoSyncComplicationData(
        type: ComplicationType,
        data: ComplicationStore,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? = buildForMode(type, LoopStatusData.LoopMode.UNKNOWN, complicationPendingIntent)

    private fun buildForMode(
        type: ComplicationType,
        mode: LoopStatusData.LoopMode,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        val appearance = mode.appearance()
        val fullLabel = getString(appearance.fullLabelRes)
        val contentDescription = PlainComplicationText.Builder(text = fullLabel).build()

        return when (type) {
            ComplicationType.SMALL_IMAGE         -> {
                // Explicit tint so the mode color does not depend on the drawable's own theme tint
                val coloredIcon = Icon.createWithResource(this, appearance.iconRes).apply { setTint(appearance.colorArgb) }
                SmallImageComplicationData.Builder(
                    smallImage = SmallImage.Builder(image = coloredIcon, type = SmallImageType.ICON).build(),
                    contentDescription = contentDescription
                )
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            ComplicationType.MONOCHROMATIC_IMAGE -> {
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = MonochromaticImage.Builder(image = Icon.createWithResource(this, appearance.iconRes)).build(),
                    contentDescription = contentDescription
                )
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                                 -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    // Preview in the watchface picker: show a closed loop instead of the UNKNOWN default
    override fun getPreviewComplicationData(): ComplicationStore =
        super.getPreviewComplicationData().let { it.copy(statusData = it.statusData.copy(loopMode = LoopStatusData.LoopMode.CLOSED)) }

    override fun getComplicationAction(): ComplicationAction = ComplicationAction.RUNNING_MODE

    override fun getProviderCanonicalName(): String = RunningModeComplication::class.java.canonicalName!!
}
