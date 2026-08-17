package app.aaps.core.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun AapsFabPreview() {
    MaterialTheme {
        AapsFab(onClick = {}) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun AapsSmallFabPreview() {
    MaterialTheme {
        AapsSmallFab(onClick = {}) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
    }
}
