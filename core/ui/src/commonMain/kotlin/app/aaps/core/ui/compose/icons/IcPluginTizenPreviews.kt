package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true)
@Composable
internal fun IcPluginTizenPreview() {
    Icon(
        imageVector = IcPluginTizen,
        contentDescription = "Tizen",
        modifier = Modifier
            .size(128.dp)
            .padding(16.dp),
        tint = Color.Black
    )
}
