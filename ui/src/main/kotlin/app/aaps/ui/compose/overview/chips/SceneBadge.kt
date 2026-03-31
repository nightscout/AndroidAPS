package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon

/**
 * Small badge indicating that a setting is managed by an active scene.
 * Shows the scene icon in a colored circle.
 */
@Composable
internal fun SceneBadge(modifier: Modifier = Modifier) {
    val sceneColor = ElementType.SCENE.color()
    Box(
        modifier = modifier
            .size(18.dp)
            .background(sceneColor.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ElementType.SCENE.icon(),
            contentDescription = null,
            tint = sceneColor,
            modifier = Modifier
                .padding(2.dp)
                .size(12.dp)
        )
    }
}
