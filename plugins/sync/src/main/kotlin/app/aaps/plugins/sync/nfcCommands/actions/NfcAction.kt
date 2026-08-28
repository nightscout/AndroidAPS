package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcCommandCode
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.nfcCommands.NfcTagStore
import org.json.JSONObject

/**
 * Base class for all NFC-triggered actions.
 * Encapsulates execution logic, UI metadata, and parameter validation.
 */
abstract class NfcAction(protected val plugin: NfcCommandsPlugin) {

    protected val uel = plugin.uel
    protected val source = Sources.NfcCommands
    
    /** Parameters for this action instance. Uses Compose State to trigger UI updates. */
    var params: JSONObject by mutableStateOf(JSONObject())

    /** Resource ID for the user-facing label of the action. */
    @StringRes open val labelResId: Int = 0
    
    /** UI theme element type for icon coloring. */
    open val elementType: ElementType = ElementType.AAPS
    
    /** List of arguments required by this action, used to build the configuration UI. */
    open val argType = listOf<ArgType>()
    
    /** Icon displayed in the UI. */
    open val icon: ImageVector = IcAaps
    
    /** Optional override for icon color. */
    open val customIconColor: (@Composable () -> Color)? = null

    /** Secondary icon to display based on current [params]. */
    open val secondaryIcon: ImageVector? get() = null

    /** Color for the secondary icon. */
    open val secondaryIconColor: (@Composable () -> Color)? get() = null

    /**
     * Executes the action using current [params].
     * @return Result containing success status and a user message.
     */
    abstract suspend fun execute(): NfcExecutionResult

    /**
     * Optional method to format current [params] into a human-readable summary.
     */
    open suspend fun formatParams(): String? = null

    /**
     * Checks if the action is currently supported (e.g., depends on pump capabilities).
     */
    open fun isSupported(): Boolean = true

    /**
     * Provides initial default parameters for the action configuration UI.
     */
    open suspend fun getDefaultParams(): JSONObject = JSONObject()

    /**
     * Serializes this action and its current parameters into a command string for NDEF storage.
     */
    fun buildCommand(code: NfcCommandCode, tagName: String): String {
        val p = JSONObject(params.toString()) // copy
        p.put(NfcJsonKeys.TAG_NAME, tagName)
        return NfcTagStore.buildCommand(code, p)
    }

    /** Helper for reporting invalid parameter formats. */
    protected fun invalidFormat(): NfcExecutionResult =
        NfcExecutionResult(false, plugin.rh.gs(R.string.wrong_format))

    /** Helper for reporting that a command cannot be executed in the current state. */
    protected fun commandNotPossible(): NfcExecutionResult =
        NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_remote_command_not_possible))
}
