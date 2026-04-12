package app.aaps.plugins.configuration.setupwizard.elements

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ConfigPluginItem
import app.aaps.core.ui.compose.ConfigPluginUiModel
import javax.inject.Inject

class SWPlugin @Inject constructor(
    aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck,
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder
) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var pType: PluginType? = null
    @StringRes private var pluginDescription = 0
    private var onPreferencesNavigate: ((pluginId: String) -> Unit)? = null

    fun option(pType: PluginType, @StringRes pluginDescription: Int): SWPlugin {
        this.pType = pType
        this.pluginDescription = pluginDescription
        return this
    }

    fun onPreferences(navigate: (pluginId: String) -> Unit): SWPlugin {
        this.onPreferencesNavigate = navigate
        return this
    }

    @Composable
    override fun Compose() {
        val pType = this.pType ?: return
        var refreshTick by remember { mutableIntStateOf(0) }
        val plugins = remember(refreshTick) { activePlugin.getSpecificPluginsVisibleInList(pType) }
        var confirmationMessage by remember { mutableStateOf<String?>(null) }
        var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        if (pluginDescription != 0) {
            Text(text = stringResource(pluginDescription))
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            plugins.forEach { plugin ->
                val pluginEnabled = remember(refreshTick) { plugin.isEnabled(pType) }
                val model = ConfigPluginUiModel(
                    id = plugin.javaClass.simpleName,
                    name = plugin.name,
                    description = plugin.description,
                    menuIcon = plugin.menuIcon,
                    composeIcon = plugin.pluginDescription.icon,
                    isEnabled = pluginEnabled,
                    canToggle = !plugin.pluginDescription.alwaysEnabled && (!pluginEnabled || isMultiSelect(pType)),
                    showPreferences = plugin.hasPreferences() && pluginEnabled
                )
                ConfigPluginItem(
                    plugin = model,
                    onPluginClick = { },
                    onEnableToggle = { enabled ->
                        val message = configBuilder.requestPluginSwitch(plugin, enabled, pType)
                        if (message != null) {
                            confirmationMessage = message
                            pendingAction = {
                                configBuilder.confirmPumpPluginSwitch(plugin, enabled, pType)
                                refreshTick++
                            }
                        } else {
                            refreshTick++
                        }
                    },
                    onPreferencesClick = { onPreferencesNavigate?.invoke(plugin.javaClass.simpleName) }
                )
            }
        }

        if (confirmationMessage != null) {
            AlertDialog(
                onDismissRequest = {
                    confirmationMessage = null
                    pendingAction = null
                },
                title = { Text(stringResource(app.aaps.core.ui.R.string.confirmation)) },
                text = { Text(confirmationMessage!!) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingAction?.invoke()
                        confirmationMessage = null
                        pendingAction = null
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        confirmationMessage = null
                        pendingAction = null
                    }) { Text(stringResource(android.R.string.cancel)) }
                }
            )
        }
    }

    private fun isMultiSelect(type: PluginType): Boolean =
        type == PluginType.GENERAL ||
            type == PluginType.CONSTRAINTS ||
            type == PluginType.LOOP ||
            type == PluginType.SYNC
}
