@file:Suppress("DEPRECATION")

package app.aaps.wear.tile

import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DeviceParametersBuilders.DeviceParameters
import androidx.wear.tiles.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
import androidx.wear.tiles.DimensionBuilders.SpProp
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.DimensionBuilders.sp
import androidx.wear.tiles.LayoutElementBuilders.Box
import androidx.wear.tiles.LayoutElementBuilders.Column
import androidx.wear.tiles.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.tiles.LayoutElementBuilders.FontStyle
import androidx.wear.tiles.LayoutElementBuilders.Image
import androidx.wear.tiles.LayoutElementBuilders.Layout
import androidx.wear.tiles.LayoutElementBuilders.LayoutElement
import androidx.wear.tiles.LayoutElementBuilders.Row
import androidx.wear.tiles.LayoutElementBuilders.Spacer
import androidx.wear.tiles.LayoutElementBuilders.Text
import androidx.wear.tiles.ModifiersBuilders.Background
import androidx.wear.tiles.ModifiersBuilders.Clickable
import androidx.wear.tiles.ModifiersBuilders.Corner
import androidx.wear.tiles.ModifiersBuilders.Modifiers
import androidx.wear.tiles.ModifiersBuilders.Semantics
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.tiles.ResourceBuilders.ImageResource
import androidx.wear.tiles.ResourceBuilders.Resources
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders.Timeline
import androidx.wear.tiles.TimelineBuilders.TimelineEntry
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

interface TileSource {

    fun getResourceReferences(resources: android.content.res.Resources): List<Int>
    fun getSelectedActions(): List<Action>
    fun getValidFor(): Long?
}

open class Action(
    val buttonText: String? = null,
    val buttonTextSub: String? = null,
    val activityClass: String,
    @DrawableRes val iconRes: Int,
    val action: EventData? = null,
    val message: String? = null,
)

enum class WearControl {
    NO_DATA, ENABLED, DISABLED
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
        val tile = Tile.Builder()
            .setResourcesVersion(resourceVersion)
            .setTimeline(
                Timeline.Builder().addTimelineEntry(
                    TimelineEntry.Builder().setLayout(
                        Layout.Builder().setRoot(layout(wearControl, actionsSelected, requestParams.deviceParameters!!)).build()
                    ).build()
                ).build()
            )

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

    @Deprecated("Deprecated in Java")
    override fun onResourcesRequest(
        requestParams: ResourcesRequest
    ): ListenableFuture<Resources> = serviceScope.future {
        Resources.Builder()
            .setVersion(resourceVersion)
            .apply {
                source.getResourceReferences(resources).forEach { resourceId ->
                    addIdToImageMapping(
                        resourceId.toString(),
                        ImageResource.Builder()
                            .setAndroidResourceByResId(
                                AndroidImageResourceByResId.Builder()
                                    .setResourceId(resourceId)
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .build()
    }

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
