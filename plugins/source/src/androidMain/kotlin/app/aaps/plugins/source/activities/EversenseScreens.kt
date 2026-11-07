package app.aaps.plugins.source.activities

import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.source.R
import app.aaps.core.ui.R as CoreR

/** The same theme, app bar and surface colors used by the AAPS settings. */
internal fun MetroAppCompatActivity.setEversenseContent(
    @StringRes title: Int,
    preferences: Preferences,
    content: @Composable () -> Unit
) {
    setContent {
        CompositionLocalProvider(LocalPreferences provides preferences) {
            AapsTheme {
                EversensePage(title, { finish() }, content)
            }
        }
    }
}

@Composable
internal fun EversensePage(@StringRes title: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(CoreR.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) { content() }
    }
}

@Composable
internal fun EversenseCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.large), content = content)
    }
}

@Composable
internal fun EversenseStatusContent(
    labels: List<String>, connected: Boolean, syncDays: Int,
    onConnect: () -> Unit, onChange: () -> Unit, onSync: () -> Unit, onDays: (Int) -> Unit
) {
    EversenseCard { labels.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) } }
    Button(onConnect, Modifier.fillMaxWidth()) {
        Text(stringResource(if (connected) R.string.eversense_status_disconnect else R.string.eversense_status_connect))
    }
    OutlinedButton(onChange, Modifier.fillMaxWidth()) { Text(stringResource(R.string.eversense_change_transmitter)) }
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.eversense_default_sync_days_label), Modifier.weight(1f))
        Box {
            OutlinedButton({ expanded = true }) { Text(syncDays.toString()) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                for (days in listOf(1, 3, 7, 14, 30)) {
                    DropdownMenuItem(text = { Text(days.toString()) }, onClick = { expanded = false; onDays(days) })
                }
            }
        }
    }
    Button(onSync, Modifier.fillMaxWidth(), enabled = connected) { Text(stringResource(R.string.eversense_information_last_sync)) }
}

@Composable
internal fun EversensePlacementContent(label: String, value: String, instruction: String, lastUpdate: String, bars: Int) {
    EversenseCard {
        Text(stringResource(R.string.eversense_placement_guide_heading), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.eversense_placement_guide_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
            repeat(5) { index ->
                Box(Modifier.width(AapsSpacing.xxLarge).height(AapsSpacing.xxLarge * (index + 1))
                    .background(if (index < bars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.shapes.small))
            }
        }
        Text(label, style = MaterialTheme.typography.headlineSmall)
        if (value.isNotEmpty()) Text(value)
        if (lastUpdate.isNotEmpty()) Text(lastUpdate, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (instruction.isNotEmpty()) Text(instruction)
    }
}
