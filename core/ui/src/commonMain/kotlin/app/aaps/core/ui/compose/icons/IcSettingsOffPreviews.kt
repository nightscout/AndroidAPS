package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun IcSettingsOffPreview() {
    MaterialTheme {
        Icon(
            imageVector = IcSettingsOff,
            contentDescription = null,
            modifier = Modifier
                .padding(8.dp)
                .size(48.dp)
        )
    }
}
