package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.plugins.sync.nfcCommands.NfcCommand
import app.aaps.plugins.sync.nfcCommands.NfcCommandCode
import app.aaps.plugins.sync.nfcCommands.NfcParams
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType

/**
 * Base class for all NFC-triggered actions.
 * Encapsulates execution logic, UI metadata, and parameter validation.
 *
 * Actions are built from the JSON stored on a tag, so they can never come out of a DI graph. They used
 * to be handed the whole plugin and reach through it, which hid what each one actually depends on.
 * Dependencies now arrive through the constructor, supplied by `NfcActionFactory`, which owns the
 * command-code-to-constructor mapping. The three below are used by every action; anything else a
 * specific action needs is on its own constructor, where it is visible.
 */
abstract class NfcAction(
    protected val aapsLogger: AAPSLogger,
    protected val rh: ResourceHelper,
    protected val uel: UserEntryLogger
) {

    protected val source = Sources.NfcCommands
    
    /** Parameters for this action instance. Uses Compose State to trigger UI updates. */
    var params: NfcParams by mutableStateOf(NfcParams())

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
     *
     * @param tagName the name of the tag that triggered this, for the user entry log.
     * @return Result containing success status and a user message.
     */
    abstract suspend fun execute(tagName: String): NfcExecutionResult

    /**
     * Optional method to format current [params] into a human-readable summary.
     */
    open suspend fun formatParams(tagName: String): String? = null

    /**
     * Checks if the action is currently supported (e.g., depends on pump capabilities).
     */
    open fun isSupported(): Boolean = true

    /**
     * Provides initial default parameters for the action configuration UI.
     */
    open suspend fun getDefaultParams(): NfcParams = NfcParams()

    /**
     * Serializes this action and its current parameters into a command string for NDEF storage.
     *
     * The tag's name is not part of a command - it belongs to the tag, and reaches an action through
     * [execute] instead.
     */
    fun buildCommand(code: NfcCommandCode): String = NfcCommand(code, params).encode()

    /** Helper for reporting invalid parameter formats. */
    protected fun invalidFormat(): NfcExecutionResult =
        NfcExecutionResult(false, rh.gs(R.string.wrong_format))

    /** Helper for reporting that a command cannot be executed in the current state. */
    protected fun commandNotPossible(): NfcExecutionResult =
        NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_command_not_possible))
}
