package app.aaps.plugins.configuration.setupwizard.elements

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
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
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ConfigPluginCard
import app.aaps.core.ui.compose.ConfigPluginUiModel
import app.aaps.core.ui.compose.SelectionMode
import dev.zacsweers.metro.Inject

class SWPlugin @Inject constructor(
    aapsLogger: AAPSLogger, rh: TextResolver, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck,
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder
) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var pType: PluginType? = null
    private var pluginDescription: TextRef? = null
    private var onPreferencesNavigate: ((pluginId: String) -> Unit)? = null
    private var onOpenPluginNavigate: ((pluginId: String) -> Unit)? = null

    fun option(pType: PluginType, pluginDescription: TextRef): SWPlugin {
        this.pType = pType
        this.pluginDescription = pluginDescription
        return this
    }

    fun onPreferences(navigate: (pluginId: String) -> Unit): SWPlugin {
        this.onPreferencesNavigate = navigate
        return this
    }

    fun onOpenPlugin(navigate: (pluginId: String) -> Unit): SWPlugin {
        this.onOpenPluginNavigate = navigate
        return this
    }

    @Composable
    override fun Compose() {
        val pType = this.pType ?: return
        var refreshTick by remember { mutableIntStateOf(0) }
        val plugins = remember(refreshTick) { activePlugin.getSpecificPluginsVisibleInList(pType) }
        var confirmationMessage by remember { mutableStateOf<String?>(null) }
        var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        pluginDescription?.let { Text(text = stringResource(it)) }

        val selectionMode =
            if (isMultiSelect(pType)) SelectionMode.MULTI_SELECT else SelectionMode.SINGLE_SELECT

        Column(modifier = Modifier.fillMaxWidth()) {
            plugins.forEach { plugin ->
                val pluginEnabled = remember(refreshTick) { plugin.isEnabled(pType) }
                val model = ConfigPluginUiModel(
                    id = plugin::class.simpleName.orEmpty(),
                    name = plugin.name,
                    description = plugin.description,
                    composeIcon = plugin.pluginDescription.icon,
                    isEnabled = pluginEnabled,
                    canToggle = !plugin.pluginDescription.alwaysEnabled && (!pluginEnabled || isMultiSelect(pType)),
                    showPreferences = plugin.hasPreferences() && pluginEnabled,
                    hasContent = plugin.hasComposeContent()
                )
                ConfigPluginCard(
                    plugin = model,
                    selectionMode = selectionMode,
                    onCardClick = {
                        val message = configBuilder.requestPluginSwitch(plugin, !pluginEnabled, pType)
                        if (message != null) {
                            confirmationMessage = message
                            pendingAction = {
                                configBuilder.confirmPumpPluginSwitch(plugin, !pluginEnabled, pType)
                                refreshTick++
                            }
                        } else {
                            refreshTick++
                        }
                    },
                    onSettingsClick = { onPreferencesNavigate?.invoke(plugin::class.simpleName.orEmpty()) },
                    onOpenPluginClick = { onOpenPluginNavigate?.invoke(plugin::class.simpleName.orEmpty()) }
                )
            }
        }

        if (confirmationMessage != null) {
            AlertDialog(
                onDismissRequest = {
                    confirmationMessage = null
                    pendingAction = null
                },
                title = { Text(stringResource(CoreUiStrings.confirmation)) },
                text = { Text(confirmationMessage!!) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingAction?.invoke()
                        confirmationMessage = null
                        pendingAction = null
                    }) { Text(stringResource(CoreUiStrings.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        confirmationMessage = null
                        pendingAction = null
                    }) { Text(stringResource(CoreUiStrings.cancel)) }
                }
            )
        }
    }

    private fun isMultiSelect(type: PluginType): Boolean = !type.singleSelect
}
