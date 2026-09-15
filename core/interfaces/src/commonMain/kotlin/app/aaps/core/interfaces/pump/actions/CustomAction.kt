package app.aaps.core.interfaces.pump.actions

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.keys.interfaces.TextRef

/**
 * A pump driver's own action, shown in the Actions tab.
 *
 * **Currently constructed by nothing, and that is intentional - do not delete it as dead code.**
 * The only producer was MedtronicPumpPlugin (wake-up-and-tune, clear-bolus-block, reset-RileyLink).
 * The extension point is kept so those actions, or a driver's equivalent, can come back without
 * redesigning the contract.
 *
 * A dead-code sweep will flag this, [CustomActionType], [app.aaps.core.interfaces.pump.Pump.getCustomActions]
 * and `Pump.executeCustomAction` together - they are one feature, retained deliberately.
 *
 * [name] is a [TextRef] rather than an Android string resource id, so this compiles for every target.
 */
data class CustomAction(
    val name: TextRef,
    val customActionType: CustomActionType,
    val icon: ImageVector,
    var isEnabled: Boolean = true
)