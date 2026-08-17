package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true)
@Composable
internal fun AapsCardPreview() {
    MaterialTheme {
        AapsCard(modifier = Modifier.padding(16.dp)) {
            Text("Card content", modifier = Modifier.padding(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun AapsCardSelectedPreview() {
    MaterialTheme {
        AapsCard(modifier = Modifier.padding(16.dp), selected = true) {
            Text("Selected card", modifier = Modifier.padding(16.dp))
        }
    }
}
