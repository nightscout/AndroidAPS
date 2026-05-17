package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.configuration.setupwizard.SWDefinition
import javax.inject.Inject

class SWPermissions @Inject constructor(
    aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck
) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var swDefinition: SWDefinition? = null

    fun with(swDefinition: SWDefinition): SWPermissions {
        this.swDefinition = swDefinition
        return this
    }

    @Composable
    override fun Compose() {
        val definition = swDefinition ?: return
        var refreshTick by remember { mutableIntStateOf(0) }

        // Refresh on resume (after returning from permission dialog/settings)
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshTick++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            val items = remember(refreshTick) {
                definition.permissionItems?.invoke() ?: emptyList()
            }
            items.forEach { (group, granted) ->
                PermissionRow(
                    group = group,
                    granted = granted,
                    onGrant = {
                        definition.onRequestPermission?.invoke(group)
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    group: PermissionGroup,
    granted: Boolean,
    onGrant: () -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        },
        headlineContent = {
            Text(stringResource(group.rationaleTitle))
        },
        supportingContent = {
            Text(
                text = stringResource(group.rationaleDescription),
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            if (!granted || group.alwaysShowAction) {
                TextButton(onClick = onGrant) {
                    Text(
                        stringResource(
                            if (granted) app.aaps.core.ui.R.string.change
                            else app.aaps.core.ui.R.string.grant
                        )
                    )
                }
            }
        }
    )
}
