package app.aaps.plugins.main.general.nfcCommands

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
    val displayLabelResId: Int = template.labelResId,
)

object NfcCategories {
    fun build(): List<NfcUiCategory> {
        val templates = NfcTokenSupport.availableCommands()

        fun byLabelResId(resId: Int) = templates.first { it.labelResId == resId }

        return listOf(
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_loop,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_loop_stop), ArgType.NONE),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_loop_resume), ArgType.NONE),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_loop_suspend), ArgType.SUSPEND),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_loop_closed), ArgType.NONE),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_loop_lgs), ArgType.NONE),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_loop,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_pump,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_pump_connect), ArgType.NONE),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_pump_disconnect), ArgType.PUMP_DISCONNECT),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_pump,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_basal,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_basal_absolute), ArgType.BASAL_ABS, R.string.nfccommands_cmd_temp_basal_absolute),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_basal_percent), ArgType.BASAL_PCT, R.string.nfccommands_cmd_temp_basal_percent),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_basal_stop), ArgType.NONE, R.string.nfccommands_cmd_temp_basal_stop),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_basal,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_bolus,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_bolus), ArgType.BOLUS),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_extended_bolus), ArgType.EXTENDED),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_extended_stop), ArgType.NONE),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_carbs), ArgType.CARBS),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_bolus,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_profile,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_profile_switch), ArgType.PROFILE),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_profile,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_targets,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_target_meal), ArgType.NONE),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_target_activity), ArgType.NONE),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_target_hypo), ArgType.NONE),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_target_stop), ArgType.NONE),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_targets,
            ),
            NfcUiCategory(
                labelResId = R.string.nfccommands_cat_system,
                commands =
                    listOf(
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_aapsclient_restart), ArgType.NONE),
                        NfcUiCommand(byLabelResId(R.string.nfccommands_cmd_restart_aaps), ArgType.NONE),
                    ),
                docAnchorResId = R.string.nfccommands_doc_anchor_system,
            ),
        )
    }
}
