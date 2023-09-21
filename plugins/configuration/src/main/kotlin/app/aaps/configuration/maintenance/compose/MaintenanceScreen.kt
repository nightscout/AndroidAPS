package app.aaps.configuration.maintenance.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.configuration.R

@Composable
fun MaintenanceScreen() {
    Column {
        // Header
        Text(
            text = "Maintenance",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(16.dp)
        )

        // Unlock settings button
        Button(
            onClick = { /* TODO: Implement unlock settings */ },
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "Unlock settings")
        }

        // Log files section
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Log files",
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(16.dp)
                )

                // Log settings button
                Button(
                    onClick = { /* TODO: Implement log settings */ },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_log_settings),
                        contentDescription = "Log settings"
                    )
                    Text(text = "Log settings")
                }

                // Send all logs button
                Button(
                    onClick = { /* TODO: Implement send all logs */ },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_send_log),
                        contentDescription = "Send all logs"
                    )
                    Text(text = "Send all logs")
                }

                // Delete logs button
                Button(
                    onClick = { /* TODO: Implement delete logs */ },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_log_delete),
                        contentDescription = "Delete logs"
                    )
                    Text(text = "Delete logs")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MaintenanceScreenPreview() {
    MaintenanceScreen()
}