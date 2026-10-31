package app.aaps.ui.widget

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Light", widthDp = 360, heightDp = 480)
@Preview(showBackground = true, name = "Dark", widthDp = 360, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun WidgetConfigureScreenPreview() {
    MaterialTheme {
        WidgetConfigureScreen(
            initialOpacity = 180,
            initialUseBlack = true,
            onOpacityChange = {},
            onUseBlackChange = {},
            onClose = {}
        )
    }
}
