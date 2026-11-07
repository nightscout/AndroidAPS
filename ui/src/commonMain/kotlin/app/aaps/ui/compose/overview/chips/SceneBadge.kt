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
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource

/**
 * Small badge indicating that a setting is managed by an active scene.
 * Shows the scene icon in a colored circle.
 *
 * The icon is not decorative: no text beside it says that a scene is in control, so it is
 * labelled. `ElementType.SCENE.label()` is null (the label is dynamic, it is the scene name),
 * so the plain scene label is used instead. The chips that show this badge are clickable
 * surfaces, so the badge label is merged into the chip announcement.
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
            contentDescription = stringResource(CoreUiStrings.scene),
            tint = sceneColor,
            modifier = Modifier
                .padding(AapsSpacing.extraSmall)
                .size(12.dp)
        )
    }
}
