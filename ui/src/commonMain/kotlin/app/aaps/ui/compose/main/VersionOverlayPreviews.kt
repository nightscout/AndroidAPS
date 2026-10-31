package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview(showBackground = true)
@Composable
internal fun VersionOverlayPreview() {
    MaterialTheme {
        Text(
            text = "3.3.0 (abc1)",
            color = Color(0xFF4CAF50),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp, end = 4.dp)
        )
    }
}
