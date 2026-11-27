package app.aaps.pump.ap550s.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing

@Composable
fun Ap550ConnectStatusScreen(
    viewModel: Ap550ConnectStatusViewModel
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(onClick = viewModel::refresh) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Text(
                    text = stringResource(R.string.refresh),
                    modifier = Modifier.padding(start = AapsSpacing.medium)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            items(rows, key = { it.time + it.source + it.description }) { row ->
                AapsCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(AapsSpacing.large)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = row.time,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = row.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = row.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}