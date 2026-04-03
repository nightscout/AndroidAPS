package app.aaps.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

@Deprecated("convert Painter -> ImageVector after migration")
@Composable
fun TonalIcon(
    painter: Painter,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .background(
                color = if (enabled) color.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = CircleShape
            )
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
