package info.nightscout.androidaps.tile

import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import androidx.core.content.ContextCompat
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DeviceParametersBuilders.DeviceParameters
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.LayoutElementBuilders.Box
import androidx.wear.tiles.LayoutElementBuilders.Column
import androidx.wear.tiles.LayoutElementBuilders.FontStyles
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
import com.google.common.util.concurrent.ListenableFuture
import info.nightscout.androidaps.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future

private const val CIRCLE_SIZE = 75f
private val ICON_SIZE = dp(25f)
private val SPACING_ACTIONS = dp(3f)
private const val BUTTON_COLOR = R.color.gray_850
const val TAG = "ASTAG-tile"

interface TileSource {
    fun getActions(): List<Action>
    fun getDefaultConfig(): Map<String, String>
}

data class Action(
    val id: Int,
    val settingName: String,
    @StringRes val nameRes: Int,
    val activityClass: String,
    @DrawableRes val iconRes: Int,
    val background: Boolean = false,
    val actionString: String? = null,
)

enum class WearControl {
    NO_DATA, ENABLED, DISABLED
}


abstract class TileBase : TileService() {

    open val resourceVersion = "1"
    open val idIconActionPrefix = "ic_action_"

    abstract val preferencePrefix: String
    abstract val source: TileSource

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<Tile> = serviceScope.future {
        val actionsSelected = getSelectedActions()
        val wearControl = getWearControl()

        Tile.Builder()
            .setResourcesVersion(resourceVersion)
            .setTimeline(
                Timeline.Builder().addTimelineEntry(
                    TimelineEntry.Builder().setLayout(
                        Layout.Builder().setRoot(layout(wearControl, actionsSelected, requestParams.deviceParameters!!)).build()
                    ).build()
                ).build()
            )
            .build()
    }

    override fun onResourcesRequest(
        requestParams: ResourcesRequest
    ): ListenableFuture<Resources> = serviceScope.future {
        Resources.Builder()
            .setVersion(resourceVersion)
            .apply {
                source.getActions().mapNotNull { action ->
                    addIdToImageMapping(
                        idIconActionPrefix + action.id,
                        ImageResource.Builder()
                            .setAndroidResourceByResId(
                                AndroidImageResourceByResId.Builder()
                                    .setResourceId(action.iconRes)
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
            val b = Column.Builder()
            if (actions.size == 1 || actions.size == 3) {
                b.addContent(addRowSingle(actions[0], deviceParameters))
            }
            if (actions.size == 4 || actions.size == 2) {
                b.addContent(addRowDouble(actions[0], actions[1], deviceParameters))
            }
            if (actions.size == 3) {
                b.addContent(addRowDouble(actions[1], actions[2], deviceParameters))
            }
            if (actions.size == 4) {
                b.addContent(Spacer.Builder().setHeight(SPACING_ACTIONS).build())
                    .addContent(addRowDouble(actions[2], actions[3], deviceParameters))
            }
            return b.build()
        }
        return Text.Builder()
            .setText(resources.getString(R.string.tile_no_config))
            .build()
    }

    private fun addRowSingle(action1: Action, deviceParameters: DeviceParameters): LayoutElement =
        Row.Builder()
            .addContent(action(action1, deviceParameters))
            .build()

    private fun addRowDouble(action1: Action, action2: Action, deviceParameters: DeviceParameters): LayoutElement =
        Row.Builder()
            .addContent(action(action1, deviceParameters))
            .addContent(Spacer.Builder().setWidth(SPACING_ACTIONS).build())
            .addContent(action(action2, deviceParameters))
            .build()

    private fun doAction(action: Action): ActionBuilders.Action {
        val inBackground = ActionBuilders.AndroidBooleanExtra.Builder().setValue(action.background).build()
        val builder = ActionBuilders.AndroidActivity.Builder()
            .setClassName(action.activityClass)
            .setPackageName(this.packageName)
            .addKeyToExtraMapping("inBackground", inBackground)
        if (action.actionString != null) {
            val actionString = ActionBuilders.AndroidStringExtra.Builder().setValue(action.actionString).build()
            builder.addKeyToExtraMapping("actionString", actionString)
        }

        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(builder.build())
            .build()
    }

    private fun action(action: Action, deviceParameters: DeviceParameters) = Box.Builder()
        .setWidth(dp(CIRCLE_SIZE))
        .setHeight(dp(CIRCLE_SIZE))
        .setModifiers(
            Modifiers.Builder()
                .setBackground(
                    Background.Builder()
                        .setColor(
                            argb(ContextCompat.getColor(baseContext, BUTTON_COLOR))
                        )
                        .setCorner(
                            Corner.Builder().setRadius(dp(CIRCLE_SIZE / 2)).build()
                        )
                        .build()
                )
                .setSemantics(
                    Semantics.Builder()
                        .setContentDescription(resources.getString(action.nameRes))
                        .build()
                )
                .setClickable(
                    Clickable.Builder()
                        .setOnClick(doAction(action))
                        .build()
                )
                .build()
        )
        .addContent(
            Column.Builder()
                .addContent(
                    Image.Builder()
                        .setWidth(ICON_SIZE)
                        .setHeight(ICON_SIZE)
                        .setResourceId(idIconActionPrefix + action.id)
                        .build()
                ).addContent(
                    Text.Builder()
                        .setText(resources.getString(action.nameRes))
                        .setFontStyle(
                            FontStyles
                                .button(deviceParameters)
                                .setColor(
                                    argb(ContextCompat.getColor(baseContext, R.color.white))
                                )
                                .build()
                        )
                        .build()
                ).build()
        )
        .build()

    private fun getWearControl(): WearControl {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!sharedPrefs.contains("wearcontrol")) {
            return WearControl.NO_DATA
        }
        val wearControlPref = sharedPrefs.getBoolean("wearcontrol", false)
        if (wearControlPref) {
            return WearControl.ENABLED
        }
        return WearControl.DISABLED
    }

    private fun getSelectedActions(): List<Action> {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        setDefaultSettings(sharedPrefs)

        val actionList: MutableList<Action> = mutableListOf()
        for (i in 1..4) {
            val action = getActionFromPreference(sharedPrefs, i)
            if (action != null) {
                actionList.add(action)
            }
        }
        if (actionList.isEmpty()) {
            return source.getActions().take(4)
        }
        return actionList
    }

    private fun getActionFromPreference(sharedPrefs: SharedPreferences, index: Int): Action? {
        val actionPref = sharedPrefs.getString(preferencePrefix + index, "none")
        return source.getActions().find { action -> action.settingName == actionPref }
    }

    private fun setDefaultSettings(sharedPrefs: SharedPreferences) {
        val defaults = source.getDefaultConfig()
        val firstKey = defaults.firstNotNullOf { settings -> settings.key }
        if (!sharedPrefs.contains(firstKey)) {
            val editor = sharedPrefs.edit()
            for ((key, value) in defaults) {
                editor.putString(key, value)
            }
            editor.apply()
        }
    }

}
