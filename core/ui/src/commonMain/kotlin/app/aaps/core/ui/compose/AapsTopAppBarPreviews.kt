package app.aaps.core.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Light")
@Composable
internal fun AapsTopAppBarPreview() {
    MaterialTheme {
        AapsTopAppBar(
            title = { Text("Screen Title") }
        )
    }
}

@Preview(showBackground = true, name = "Light - Nav & Actions")
@Composable
internal fun AapsTopAppBarWithNavAndActionsPreview() {
    MaterialTheme {
        AapsTopAppBar(
            title = { Text("Screen Title") },
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
            }
        )
    }
}
