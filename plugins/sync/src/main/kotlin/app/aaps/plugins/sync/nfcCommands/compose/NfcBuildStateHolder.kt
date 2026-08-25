package app.aaps.plugins.sync.nfcCommands.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.nfcCommands.NfcTagStore

/**
 * Everything the tag build screen remembers while the user is editing.
 *
 * It used to be a dozen `remember { mutableStateOf(...) }` values declared inside the composable,
 * which made [isDirty] - the rule that decides whether leaving the screen needs a confirmation -
 * reachable only by driving the UI. Holding it here lets that rule be read and tested on its own.
 *
 * Same shape as `plugins/automation/.../compose/AutomationStateHolder.kt`.
 */
class NfcBuildStateHolder {

    /** Commands queued for the tag, in the order they will run. */
    val chain = mutableStateListOf<NfcUiAction>()

    var tagName by mutableStateOf("")

    /** True while the "hold your phone near the tag" dialog is up. */
    var isWritingMode by mutableStateOf(false)

    var showBlankNameDialog by mutableStateOf(false)
    var showActionPicker by mutableStateOf(false)
    var showDiscardConfirm by mutableStateOf(false)
    var showOverwriteConfirm by mutableStateOf(false)

    /** The tag being overwritten, and the name it currently carries, for the confirmation. */
    var overwriteUid by mutableStateOf("")
    var overwriteExistingName by mutableStateOf("")

    /** What the screen loaded with. [isDirty] compares against these. */
    var initialCommandsSnap by mutableStateOf<List<String>>(emptyList())
    var initialTagNameSnap by mutableStateOf("")

    /** False until the screen has finished loading, so an empty screen does not look edited. */
    var isInitialized by mutableStateOf(false)

    /** The chain serialised the way it would be written to a tag. */
    val currentCommands: List<String>
        get() = chain.map { action ->
            val p = action.getParams()
            NfcTagStore.buildCommand(action.command, p.apply { put(NfcJsonKeys.TAG_NAME, tagName) })
        }

    /** True when something has changed since the screen loaded. Drives the discard confirmation. */
    val isDirty: Boolean
        get() = isInitialized && (tagName != initialTagNameSnap || currentCommands != initialCommandsSnap)
}

@Composable
fun rememberNfcBuildState(): NfcBuildStateHolder = remember { NfcBuildStateHolder() }
