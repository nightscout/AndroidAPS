package app.aaps.plugins.automation.compose.actions

import kotlin.reflect.KClass
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.automation.AutomationStrings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionAlarm
import app.aaps.plugins.automation.actions.ActionCarePortalEvent
import app.aaps.plugins.automation.actions.ActionDisableScene
import app.aaps.plugins.automation.actions.ActionEnableScene
import app.aaps.plugins.automation.actions.ActionNotification
import app.aaps.plugins.automation.actions.ActionProfileSwitch
import app.aaps.plugins.automation.actions.ActionProfileSwitchPercent
import app.aaps.plugins.automation.actions.ActionRunAutotune
import app.aaps.plugins.automation.actions.ActionRunScene
import app.aaps.plugins.automation.actions.ActionSMBChange
import app.aaps.plugins.automation.actions.ActionSendSMS
import app.aaps.plugins.automation.actions.ActionSettingsExport
import app.aaps.plugins.automation.actions.ActionStartTempTarget
import app.aaps.plugins.automation.actions.ActionStopProcessing
import app.aaps.plugins.automation.actions.ActionStopTempTarget
import app.aaps.plugins.automation.compose.iconColor

enum class ActionCategory(val labelResId: TextRef) {
    Targets(AutomationStrings.automation_category_targets),
    Profile(AutomationStrings.automation_category_profile),
    Loop(AutomationStrings.automation_category_loop),
    Scenes(AutomationStrings.automation_category_scenes),
    Alerts(AutomationStrings.automation_category_alerts),
    System(AutomationStrings.automation_category_system),
    Other(AutomationStrings.automation_category_other)
}

private fun actionCategoryOf(cls: KClass<*>): ActionCategory = when (cls) {
    ActionStartTempTarget::class,
    ActionStopTempTarget::class -> ActionCategory.Targets

    ActionProfileSwitch::class,
    ActionProfileSwitchPercent::class -> ActionCategory.Profile

    ActionSMBChange::class,
    ActionStopProcessing::class,
    ActionRunAutotune::class -> ActionCategory.Loop

    ActionRunScene::class,
    ActionEnableScene::class,
    ActionDisableScene::class -> ActionCategory.Scenes

    ActionAlarm::class,
    ActionNotification::class,
    ActionSendSMS::class,
    ActionCarePortalEvent::class -> ActionCategory.Alerts

    ActionSettingsExport::class -> ActionCategory.System
    else -> ActionCategory.Other
}

data class ActionOption(
    val className: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val prototype: Action,
    val category: ActionCategory
) {

    companion object {

        fun from(action: Action): ActionOption = ActionOption(
            className = action::class.simpleName.orEmpty(),
            label = action.shortDescription().substringBefore(':').trim().ifEmpty { action::class.simpleName.orEmpty() },
            icon = action.composeIcon(),
            prototype = action,
            category = actionCategoryOf(action::class)
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
                .consumeOverscroll()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(AutomationStrings.automation_choose_action),
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
                                        tint = opt.prototype.iconColor(),
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
