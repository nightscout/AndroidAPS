package app.aaps.plugins.source.eversense

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.source.R

/** Compose replacement for EversenseFragment; PR #191's actions are still placeholders. */
internal class EversenseComposeContent(private val rh: ResourceHelper) : ComposablePluginContent {
    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val context = LocalContext.current
        val title = stringResource(R.string.source_eversense)
        LaunchedEffect(title, onNavigateBack, onSettings) {
            setToolbarConfig(SelectableListToolbar(
                isRemovingMode = false,
                selectedCount = 0,
                onExitRemovingMode = {},
                onNavigateBack = onNavigateBack,
                onDelete = {},
                rh = rh,
                title = title,
                onSettings = onSettings
            ))
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.eversense_not_implemented))
            Text(stringResource(R.string.eversense_my_glucose), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                Toast.makeText(context, R.string.eversense_calibration_message, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.eversense_calibrate)) }
            Text(stringResource(R.string.eversense_calibrate_summary))
            TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.eversense_history)) }
            Text(stringResource(R.string.eversense_device), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                Toast.makeText(context, R.string.eversense_connect_message, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.eversense_connect)) }
            Text(stringResource(R.string.eversense_connect_summary))
            Text(stringResource(R.string.eversense_configuration), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onSettings?.invoke() }, enabled = onSettings != null) {
                Text(stringResource(R.string.eversense_glucose_settings))
            }
        }
    }
}
