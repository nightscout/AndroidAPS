package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun careportalPreview() {
    Icon(
        imageVector = Careportal,
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp)
    )
}
