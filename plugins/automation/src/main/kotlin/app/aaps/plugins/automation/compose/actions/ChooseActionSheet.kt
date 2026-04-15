package app.aaps.plugins.automation.compose.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionAlarm
import app.aaps.plugins.automation.actions.ActionCarePortalEvent
import app.aaps.plugins.automation.actions.ActionNotification
import app.aaps.plugins.automation.actions.ActionProfileSwitch
import app.aaps.plugins.automation.actions.ActionProfileSwitchPercent
import app.aaps.plugins.automation.actions.ActionRunAutotune
import app.aaps.plugins.automation.actions.ActionSMBChange
import app.aaps.plugins.automation.actions.ActionSendSMS
import app.aaps.plugins.automation.actions.ActionSettingsExport
import app.aaps.plugins.automation.actions.ActionStartTempTarget
import app.aaps.plugins.automation.actions.ActionStopProcessing
import app.aaps.plugins.automation.actions.ActionStopTempTarget

enum class ActionCategory(val labelResId: Int) {
    Targets(R.string.automation_category_targets),
    Profile(R.string.automation_category_profile),
    Loop(R.string.automation_category_loop),
    Alerts(R.string.automation_category_alerts),
    System(R.string.automation_category_system),
    Other(R.string.automation_category_other)
}

private fun actionCategoryOf(cls: Class<*>): ActionCategory = when (cls) {
    ActionStartTempTarget::class.java,
    ActionStopTempTarget::class.java -> ActionCategory.Targets

    ActionProfileSwitch::class.java,
    ActionProfileSwitchPercent::class.java -> ActionCategory.Profile

    ActionSMBChange::class.java,
    ActionStopProcessing::class.java,
    ActionRunAutotune::class.java -> ActionCategory.Loop

    ActionAlarm::class.java,
    ActionNotification::class.java,
    ActionSendSMS::class.java,
    ActionCarePortalEvent::class.java -> ActionCategory.Alerts

    ActionSettingsExport::class.java -> ActionCategory.System
    else -> ActionCategory.Other
}

data class ActionOption(
    val className: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val iconTint: androidx.compose.ui.graphics.Color?,
    val category: ActionCategory
) {

    companion object {

        fun from(action: Action): ActionOption = ActionOption(
            className = action.javaClass.name,
            label = action.shortDescription().substringBefore(':').trim().ifEmpty { action.javaClass.simpleName },
            icon = action.composeIcon(),
            iconTint = action.composeIconTint(),
            category = actionCategoryOf(action.javaClass)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChooseActionSheet(
    options: List<ActionOption>,
    onPick: (ActionOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.automation_choose_action),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            val grouped = options.groupBy { it.category }
            ActionCategory.entries.forEach { cat ->
                val items = grouped[cat].orEmpty()
                if (items.isEmpty()) return@forEach
                Text(
                    text = stringResource(cat.labelResId),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items.forEach { opt ->
                        AssistChip(
                            onClick = {
                                onPick(opt)
                                onDismiss()
                            },
                            label = { Text(opt.label) },
                            leadingIcon = opt.icon?.let {
                                {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = null,
                                        tint = opt.iconTint ?: MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
