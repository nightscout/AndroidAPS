package app.aaps.plugins.main.general.nfcCommands

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCancelExtendedBolus
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcExtendedBolus
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.icons.IcLoopDisabled
import app.aaps.core.ui.compose.icons.IcLoopDisconnected
import app.aaps.core.ui.compose.icons.IcLoopLgs
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.icons.IcLoopReconnect
import app.aaps.core.ui.compose.icons.IcPluginNsClient
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.icons.IcTbrCancel
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTbrLow
import app.aaps.core.ui.compose.icons.IcTtActivity
import app.aaps.core.ui.compose.icons.IcTtCancel
import app.aaps.core.ui.compose.icons.IcTtEatingSoon
import app.aaps.core.ui.compose.icons.IcTtHypo
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.plugins.main.R

enum class ArgType { NONE, SUSPEND, PUMP_DISCONNECT, BOLUS, BASAL_ABS, BASAL_PCT, EXTENDED, CARBS, PROFILE }

data class NfcUiCategory(
    val labelResId: Int,
    val commands: List<NfcUiCommand>,
    val docAnchorResId: Int,
)

data class NfcUiCommand(
    val template: NfcCommandTemplate,
    val argType: ArgType,
    val icon: ImageVector,
    val elementType: ElementType,
    val displayLabelResId: Int = template.labelResId,
)

object NfcCategories {
    fun build(): List<NfcUiCategory> {
        val templates = NfcTagStore.availableCommands()

        fun byLabelResId(resId: Int) = templates.first { it.labelResId == resId }

        return listOf(
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_loop,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_loop_stop), ArgType.NONE, IcLoopDisabled, ElementType.LOOP),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_loop_resume), ArgType.NONE, IcLoopClosed, ElementType.LOOP),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_loop_suspend), ArgType.SUSPEND, IcLoopPaused, ElementType.LOOP),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_loop_closed), ArgType.NONE, IcLoopClosed, ElementType.LOOP),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_loop_lgs), ArgType.NONE, IcLoopLgs, ElementType.LOOP),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_loop,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_pump,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_pump_connect), ArgType.NONE, IcLoopReconnect, ElementType.PUMP),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_pump_disconnect), ArgType.PUMP_DISCONNECT, IcLoopDisconnected, ElementType.PUMP),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_pump,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_basal,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_basal_absolute), ArgType.BASAL_ABS, IcTbrHigh, ElementType.TEMP_BASAL, R.string.nfccommands_cmd_temp_basal_absolute),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_basal_percent), ArgType.BASAL_PCT, IcTbrLow, ElementType.TEMP_BASAL, R.string.nfccommands_cmd_temp_basal_percent),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_basal_stop), ArgType.NONE, IcTbrCancel, ElementType.TEMP_BASAL, R.string.nfccommands_cmd_temp_basal_stop),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_basal,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_bolus,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_bolus), ArgType.BOLUS, IcBolus, ElementType.INSULIN),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_extended_bolus), ArgType.EXTENDED, IcExtendedBolus, ElementType.EXTENDED_BOLUS),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_extended_stop), ArgType.NONE, IcCancelExtendedBolus, ElementType.EXTENDED_BOLUS),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_carbs), ArgType.CARBS, IcCarbs, ElementType.CARBS),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_bolus,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_profile,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_profile_switch), ArgType.PROFILE, IcProfile, ElementType.PROFILE_MANAGEMENT),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_profile,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_targets,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_target_meal), ArgType.NONE, IcTtEatingSoon, ElementType.TEMP_TARGET_MANAGEMENT),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_target_activity), ArgType.NONE, IcTtActivity, ElementType.TEMP_TARGET_MANAGEMENT),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_target_hypo), ArgType.NONE, IcTtHypo, ElementType.TEMP_TARGET_MANAGEMENT),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_target_stop), ArgType.NONE, IcTtCancel, ElementType.TEMP_TARGET_MANAGEMENT),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_targets,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_system,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_aapsclient_restart), ArgType.NONE, IcPluginNsClient, ElementType.SETTINGS),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_restart_aaps), ArgType.NONE, IcAaps, ElementType.AAPS),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_system,
            ),
        )
    }
}
