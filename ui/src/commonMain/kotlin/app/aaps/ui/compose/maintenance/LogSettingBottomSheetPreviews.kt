package app.aaps.ui.compose.maintenance

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.logging.LogElement

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
internal fun LogSettingBottomSheetContentPreview() {
    MaterialTheme {
        LogSettingBottomSheetContent(
            logElements = listOf(
                PreviewLogElement("APS"),
                PreviewLogElement("Pump", enabled = false),
                PreviewLogElement("Core"),
                PreviewLogElement("UI", enabled = false),
                PreviewLogElement("Notification"),
                PreviewLogElement("Database"),
                PreviewLogElement("Worker")
            ),
            onToggle = { _, _ -> },
            onResetToDefaults = {}
        )
    }
}

private class PreviewLogElement(
    override var name: String,
    override var defaultValue: Boolean = true,
    override var enabled: Boolean = true
) : LogElement {

    constructor(name: String, enabled: Boolean) : this(name, true, enabled)

    override fun enable(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun resetToDefault() {
        enabled = defaultValue
    }
}
