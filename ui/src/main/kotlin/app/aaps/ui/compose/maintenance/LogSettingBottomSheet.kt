package app.aaps.ui.compose.maintenance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.logging.LogElement
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSettingBottomSheet(
    logElements: List<LogElement>,
    onDismiss: () -> Unit,
    onToggle: (LogElement, Boolean) -> Unit,
    onResetToDefaults: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LogSettingBottomSheetContent(
            logElements = logElements,
            onToggle = onToggle,
            onResetToDefaults = onResetToDefaults
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogSettingBottomSheetContent(
    logElements: List<LogElement>,
    onToggle: (LogElement, Boolean) -> Unit,
    onResetToDefaults: () -> Unit
) {
    val checkedStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            logElements.forEach { put(it.name, it.enabled) }
        }
    }

    Column(
        modifier = Modifier
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = stringResource(CoreUiR.string.nav_logsettings),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            logElements.forEach { element ->
                val selected = checkedStates[element.name] ?: element.enabled
                FilterChip(
                    selected = selected,
                    onClick = {
                        val newValue = !selected
                        checkedStates[element.name] = newValue
                        onToggle(element, newValue)
                    },
                    label = { Text(text = element.name) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        OutlinedButton(
            onClick = {
                onResetToDefaults()
                logElements.forEach { checkedStates[it.name] = it.defaultValue }
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(CoreUiR.string.resettodefaults),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
private fun LogSettingBottomSheetContentPreview() {
    MaterialTheme {
        LogSettingBottomSheetContent(
            logElements = listOf(
                PreviewLogElement("APS"),
                PreviewLogElement("Pump", enabled = false),
                PreviewLogElement("Core"),
                PreviewLogElement("UI", enabled = false),
                PreviewLogElement("Notification"),
                PreviewLogElement("Database"),
                PreviewLogElement("Worker")
            ),
            onToggle = { _, _ -> },
            onResetToDefaults = {}
        )
    }
}

private class PreviewLogElement(
    override var name: String,
    override var defaultValue: Boolean = true,
    override var enabled: Boolean = true
) : LogElement {

    constructor(name: String, enabled: Boolean) : this(name, true, enabled)

    override fun enable(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun resetToDefault() {
        enabled = defaultValue
    }
}
