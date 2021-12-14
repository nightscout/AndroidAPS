package info.nightscout.androidaps.tile

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

private const val RESOURCES_VERSION = "1"
private const val ID_IC_ACTION_PREFIX = "ic_action_"
private const val CIRCLE_SIZE = 75f
private val ICON_SIZE = dp(25f)
private val SPACING_ACTIONS = dp(3f)
private const val BUTTON_COLOR = R.color.gray_850

class ActionsTileService : TileService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<Tile> = serviceScope.future {
        val actions = ActionSource.getActions().take(4)
        Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTimeline(
                Timeline.Builder().addTimelineEntry(
                    TimelineEntry.Builder().setLayout(
                        Layout.Builder().setRoot(layout(actions, requestParams.deviceParameters!!)).build()
                    ).build()
                ).build()
            )
            .build()
    }

    override fun onResourcesRequest(
        requestParams: ResourcesRequest
    ): ListenableFuture<Resources> = serviceScope.future {
        val actions = ActionSource.getActions().take(4)
        Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .apply {
                actions.mapNotNull { action ->
                    addIdToImageMapping(
                        ID_IC_ACTION_PREFIX + action.id,
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

    private fun layout(actions: List<Action>, deviceParameters: DeviceParameters): LayoutElement =
        Column.Builder()
            .addContent(
                Row.Builder()
                    .addContent(action(actions[0], deviceParameters))
                    .addContent(Spacer.Builder().setWidth(SPACING_ACTIONS).build())
                    .addContent(action(actions[1], deviceParameters))
                    .build()
            )
            .addContent(Spacer.Builder().setHeight(SPACING_ACTIONS).build())
            .addContent(
                Row.Builder()
                    .addContent(action(actions[2], deviceParameters))
                    .addContent(Spacer.Builder().setWidth(SPACING_ACTIONS).build())
                    .addContent(action(actions[3], deviceParameters))
                    .build()
            )
            .build()

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
                        .setOnClick(
                            ActionBuilders.LaunchAction.Builder()
                                .setAndroidActivity(
                                    ActionBuilders.AndroidActivity.Builder()
                                        .setClassName(action.activityClass)
                                        .setPackageName(this.packageName)
                                        .build()
                                )
                                .build()
                        )
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
                        .setResourceId(ID_IC_ACTION_PREFIX + action.id)
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

}
