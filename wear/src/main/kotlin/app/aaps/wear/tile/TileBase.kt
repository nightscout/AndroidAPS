@file:Suppress("DEPRECATION")

package app.aaps.wear.tile

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
import androidx.wear.protolayout.DimensionBuilders.SpProp
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Semantics
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import com.google.common.util.concurrent.ListenableFuture
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import javax.inject.Inject
import kotlin.math.sqrt

private const val SPACING_ACTIONS = 3f
private const val ICON_SIZE_FRACTION = 0.4f // Percentage of button diameter
private val BUTTON_COLOR = R.color.gray_850
private const val LARGE_SCREEN_WIDTH_DP = 210

/**
 * Data source for Wear OS tiles.
 *
 * Tiles are interactive glanceable surfaces that display information and actions
 * directly on the watch face carousel. TileSource defines the contract for
 * providing tile content, resources, and refresh behavior.
 *
 * Implementations provide:
 * - Actions to display (buttons with icons and text)
 * - Resource references for images
 * - Refresh interval (how long tile data remains valid)
 */
interface TileSource {

    /**
     * Get list of drawable resource IDs used by this tile.
     *
     * These resources are bundled with the tile and made available
     * for rendering. Typically includes button icons and status indicators.
     *
     * @param resources Android resources for accessing drawables
     * @return List of drawable resource IDs (e.g., R.drawable.ic_bolus)
     */
    fun getResourceReferences(resources: Resources): List<Int>

    /**
     * Get list of actions to display on the tile.
     *
     * Actions are rendered as interactive buttons. The tile layout
     * automatically arranges 1-4 actions in appropriate grid patterns:
     * - 1 action: Single centered button
     * - 2 actions: Two buttons side-by-side
     * - 3 actions: One on top, two on bottom
     * - 4 actions: 2x2 grid
     *
     * @return List of 1-4 actions to display, or empty for no actions
     */
    fun getSelectedActions(): List<Action>

    /**
     * Get validity duration for tile data in milliseconds.
     *
     * Determines how long the system can cache tile data before
     * requesting a refresh. Shorter intervals ensure fresher data
     * but consume more battery.
     *
     * @return Duration in milliseconds, or null for no automatic refresh
     */
    fun getValidFor(): Long?
}

/**
 * Defines an interactive action button on a Wear OS tile.
 *
 * Actions are rendered as circular buttons with icons and optional text.
 * When tapped, they launch the specified activity with optional data payload.
 *
 * @param buttonText Primary text label displayed on the button (e.g., "Bolus")
 * @param buttonTextSub Secondary text label displayed below primary (e.g., "5.2U")
 * @param activityClass Fully qualified class name of activity to launch (e.g., "app.aaps.wear.MyActivity")
 * @param iconRes Drawable resource ID for the button icon
 * @param action Optional event data to pass to the launched activity
 * @param message Optional message string to pass to the launched activity
 */
open class Action(
    val buttonText: String? = null,
    val buttonTextSub: String? = null,
    val activityClass: String,
    @DrawableRes val iconRes: Int,
    val action: EventData? = null,
    val message: String? = null,
)

/**
 * Wear control state indicating whether remote control is enabled and data is available.
 *
 * Determines what content the tile displays:
 * - ENABLED: Normal operation, show action buttons
 * - DISABLED: Wear control not enabled in preferences, show message
 * - NO_DATA: Wear control enabled but no data received from phone, show message
 */
enum class WearControl {
    /** No data received from phone yet */
    NO_DATA,

    /** Wear control enabled and data available, show actions */
    ENABLED,

    /** Wear control disabled in app preferences */
    DISABLED
}

abstract class TileBase : TileService() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var aapsLogger: AAPSLogger

    abstract val resourceVersion: String
    abstract val source: TileSource

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<Tile> = serviceScope.future {
        val actionsSelected = getSelectedActions()
        val wearControl = getWearControl()
        val deviceParams = requestParams.deviceConfiguration

        // Build layout using protolayout (non-deprecated)
        val layoutElement = layout(wearControl, actionsSelected, deviceParams)

        // Create protolayout Timeline from LayoutElement
        val protoTimeline = Timeline.fromLayoutElement(layoutElement)

        val tile = Tile.Builder()
            .setResourcesVersion(resourceVersion)
            .setTileTimeline(protoTimeline)

        val validFor = validFor()
        if (validFor != null) {
            tile.setFreshnessIntervalMillis(validFor)
        }
        tile.build()
    }

    private fun getSelectedActions(): List<Action> {
        // TODO check why thi scan not be don in scope of the coroutine
        return source.getSelectedActions()
    }

    private fun validFor(): Long? {
        return source.getValidFor()
    }

    @Deprecated("Deprecated in TileService but still required for now")
    override fun onResourcesRequest(
        requestParams: ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = serviceScope.future {
        // Build resources using tiles (Resources are simple references, no complex UI)
        ResourceBuilders.Resources.Builder()
            .setVersion(resourceVersion)
            .apply {
                source.getResourceReferences(resources).forEach { resourceId ->
                    addIdToImageMapping(
                        resourceId.toString(),
                        ResourceBuilders.ImageResource.Builder()
                            .setAndroidResourceByResId(
                                ResourceBuilders.AndroidImageResourceByResId.Builder()
                                    .setResourceId(resourceId)
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .build()
    }

    /**
     * Build the tile layout based on wear control state and selected actions.
     *
     * Layout algorithm:
     * - DISABLED state: Show "Wear control not enabled" message
     * - NO_DATA state: Show "No data" message
     * - ENABLED state with actions: Arrange buttons in grid pattern:
     *   - 1 action: Single centered button
     *   - 2 actions: Two buttons side-by-side
     *   - 3 actions: One on top row, two on bottom row
     *   - 4 actions: 2x2 grid with vertical spacing
     * - ENABLED state with no actions: Show "No configuration" message
     *
     * @param wearControl Current wear control state
     * @param actions List of actions to display (0-4 actions)
     * @param deviceParameters Screen dimensions and shape
     * @return Layout element to render
     */
    private fun layout(wearControl: WearControl, actions: List<Action>, deviceParameters: DeviceParameters): LayoutElement {
        if (wearControl == WearControl.DISABLED) {
            return Text.Builder()
                .setText(resources.getString(R.string.wear_control_not_enabled))
                .build()
        } else if (wearControl == WearControl.NO_DATA) {
            return Text.Builder()
                .setText(resources.getString(R.string.wear_control_no_data))
                .build()
        }
        if (actions.isNotEmpty()) {
            with(Column.Builder()) {
                if (actions.size == 1 || actions.size == 3) {
                    addContent(addRowSingle(actions[0], deviceParameters))
                }
                if (actions.size == 4 || actions.size == 2) {
                    addContent(addRowDouble(actions[0], actions[1], deviceParameters))
                }
                if (actions.size == 3) {
                    addContent(addRowDouble(actions[1], actions[2], deviceParameters))
                }
                if (actions.size == 4) {
                    addContent(Spacer.Builder().setHeight(dp(SPACING_ACTIONS)).build())
                    addContent(addRowDouble(actions[2], actions[3], deviceParameters))
                }
                return build()
            }
        }
        return Text.Builder()
            .setText(resources.getString(R.string.tile_no_config))
            .build()
    }

    private fun addRowSingle(action: Action, deviceParameters: DeviceParameters): LayoutElement =
        Row.Builder()
            .addContent(action(action, deviceParameters))
            .build()

    private fun addRowDouble(action1: Action, action2: Action, deviceParameters: DeviceParameters): LayoutElement =
        Row.Builder()
            .addContent(action(action1, deviceParameters))
            .addContent(Spacer.Builder().setWidth(dp(SPACING_ACTIONS)).build())
            .addContent(action(action2, deviceParameters))
            .build()

    private fun doAction(action: Action): ActionBuilders.Action {
        val builder = ActionBuilders.AndroidActivity.Builder()
            .setClassName(action.activityClass)
            .setPackageName(this.packageName)
        if (action.action != null) {
            val actionString = ActionBuilders.AndroidStringExtra.Builder().setValue(action.action.serialize()).build()
            builder.addKeyToExtraMapping(DataLayerListenerServiceWear.KEY_ACTION, actionString)
        }
        if (action.message != null) {
            val message = ActionBuilders.AndroidStringExtra.Builder().setValue(action.message).build()
            builder.addKeyToExtraMapping(DataLayerListenerServiceWear.KEY_MESSAGE, message)
        }

        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(builder.build())
            .build()
    }

    /**
     * Create an interactive circular button for a tile action.
     *
     * The button consists of:
     * - Circular background with diameter calculated for optimal screen fit
     * - Icon scaled to 40% of button diameter
     * - Optional primary and secondary text labels
     * - Click handler that launches the specified activity
     * - Accessibility semantics for screen readers
     *
     * @param action Action definition with icon, text, and launch target
     * @param deviceParameters Screen dimensions for sizing calculations
     * @return Box element containing the styled, interactive button
     */
    private fun action(action: Action, deviceParameters: DeviceParameters): LayoutElement {
        val circleDiameter = circleDiameter(deviceParameters)
        val text = action.buttonText
        val textSub = action.buttonTextSub
        return Box.Builder()
            .setWidth(dp(circleDiameter))
            .setHeight(dp(circleDiameter))
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(ContextCompat.getColor(baseContext, BUTTON_COLOR)))
                            .setCorner(Corner.Builder().setRadius(dp(circleDiameter / 2)).build())
                            .build()
                    )
                    .setSemantics(
                        Semantics.Builder()
                            .setContentDescription("$text $textSub")
                            .build()
                    )
                    .setClickable(
                        Clickable.Builder()
                            .setOnClick(doAction(action))
                            .build()
                    )
                    .build()
            )
            .addContent(addTextContent(action, deviceParameters))
            .build()
    }

    private fun addTextContent(action: Action, deviceParameters: DeviceParameters): LayoutElement {
        val circleDiameter = circleDiameter(deviceParameters)
        val iconSize = dp(circleDiameter * ICON_SIZE_FRACTION)
        val text = action.buttonText
        val textSub = action.buttonTextSub
        val image = Image.Builder()
            .setWidth(iconSize)
            .setHeight(iconSize)
            .setResourceId(action.iconRes.toString())
            .build()

        if (text == null && textSub == null) {
            return image
        }

        val col = Column.Builder()
            .addContent(image)
        if (text != null) {
            col.addContent(
                Text.Builder()
                    .setText(text)
                    .setFontStyle(
                        FontStyle.Builder()
                            .setWeight(FONT_WEIGHT_BOLD)
                            .setColor(argb(ContextCompat.getColor(baseContext, R.color.white)))
                            .setSize(buttonTextSize(deviceParameters, text))
                            .build()
                    )
                    .build()
            )
        }
        if (textSub != null) {
            col.addContent(
                Text.Builder()
                    .setText(textSub)
                    .setFontStyle(
                        FontStyle.Builder()
                            .setColor(argb(ContextCompat.getColor(baseContext, R.color.white)))
                            .setSize(buttonTextSize(deviceParameters, textSub))
                            .build()
                    )
                    .build()
            )
        }

        return col.build()
    }

    /**
     * Calculate optimal circular button diameter for the device screen.
     *
     * Geometry:
     * - Round screens: Use inscribed square method
     *   - Diameter = (√2 - 1) × screen_height
     *   - This fits buttons in corners of the inscribed square
     * - Square screens: Use half-height
     *   - Diameter = 0.5 × screen_height
     *   - Allows 2×2 grid to fit comfortably
     *
     * Both formulas subtract spacing to prevent edge clipping.
     *
     * @param deviceParameters Screen dimensions and shape
     * @return Button diameter in DP
     */
    private fun circleDiameter(deviceParameters: DeviceParameters) = when (deviceParameters.screenShape) {
        SCREEN_SHAPE_ROUND -> ((sqrt(2f) - 1) * deviceParameters.screenHeightDp) - (2 * SPACING_ACTIONS)
        else               -> 0.5f * deviceParameters.screenHeightDp - SPACING_ACTIONS
    }

    private fun buttonTextSize(deviceParameters: DeviceParameters, text: String): SpProp {
        if (text.length > 6) {
            return sp(if (isLargeScreen(deviceParameters)) 14f else 12f)
        }
        return sp(if (isLargeScreen(deviceParameters)) 16f else 14f)
    }

    /**
     * Determine if device has a large screen (≥210dp width).
     *
     * Used to adjust text sizes for better readability:
     * - Large screens: Larger text sizes (14-16sp)
     * - Small screens: Smaller text sizes (12-14sp)
     *
     * Threshold based on typical Wear OS device classifications.
     *
     * @param deviceParameters Screen dimensions
     * @return true if screen width ≥ 210dp
     */
    private fun isLargeScreen(deviceParameters: DeviceParameters): Boolean {
        return deviceParameters.screenWidthDp >= LARGE_SCREEN_WIDTH_DP
    }

    private fun getWearControl(): WearControl {
        if (preferences.getIfExists(BooleanKey.WearControl) == null) {
            return WearControl.NO_DATA
        }
        val wearControlPref = preferences.get(BooleanKey.WearControl)
        if (wearControlPref) {
            return WearControl.ENABLED
        }
        return WearControl.DISABLED
    }

}
