package app.aaps.ui.compose.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.descriptionResId
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId

@Composable
fun MainDrawer(
    versionName: String,
    appIcon: Int,
    onNavigate: (NavigationRequest) -> Unit,
    isTreatmentsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier.width(320.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Image(
                painter = painterResource(id = appIcon),
                contentDescription = "AAPS Logo",
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "AAPS $versionName",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            DrawerMenuItem(ElementType.TREATMENTS, enabled = isTreatmentsEnabled) { onNavigate(NavigationRequest.Element(ElementType.TREATMENTS)) }
            DrawerMenuItem(ElementType.HISTORY_BROWSER) { onNavigate(NavigationRequest.Element(ElementType.HISTORY_BROWSER)) }
            DrawerMenuItem(ElementType.STATISTICS) { onNavigate(NavigationRequest.Element(ElementType.STATISTICS)) }
            DrawerMenuItem(ElementType.PROFILE_HELPER) { onNavigate(NavigationRequest.Element(ElementType.PROFILE_HELPER)) }
            DrawerMenuItem(ElementType.MAINTENANCE) { onNavigate(NavigationRequest.Element(ElementType.MAINTENANCE)) }
            DrawerMenuItem(ElementType.SETUP_WIZARD) { onNavigate(NavigationRequest.Element(ElementType.SETUP_WIZARD)) }
            DrawerMenuItem(ElementType.CONFIGURATION) { onNavigate(NavigationRequest.Element(ElementType.CONFIGURATION)) }
        }

        // Bottom section with About and Exit
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        DrawerMenuItem(ElementType.ABOUT) { onNavigate(NavigationRequest.Element(ElementType.ABOUT)) }
        DrawerMenuItem(ElementType.EXIT) { onNavigate(NavigationRequest.Element(ElementType.EXIT)) }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** DrawerMenuItem that derives icon, label, and description from [ElementType]. */
@Composable
private fun DrawerMenuItem(
    elementType: ElementType,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val descResId = elementType.descriptionResId()
    DrawerMenuItem(
        icon = elementType.icon(),
        label = stringResource(elementType.labelResId()),
        description = if (descResId != 0) stringResource(descResId) else null,
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun DrawerMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    description: String? = null,
    enabled: Boolean = true,
) {
    val iconPainter = icon?.let { rememberVectorPainter(it) }
        ?: error("DrawerMenuItem requires an icon")
    val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = if (description != null) 8.dp else 12.dp)
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint
                )
            }
        }
    }
}
