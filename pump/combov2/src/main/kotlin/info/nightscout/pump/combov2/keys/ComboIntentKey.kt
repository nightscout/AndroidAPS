package info.nightscout.pump.combov2.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import info.nightscout.pump.combov2.R

enum class ComboIntentKey(
    override val key: String,
    override val titleResId: Int = 0,
    override val confirmationMessageResId: Int? = null,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS
) : IntentPreferenceKey {

    // Pair button enabled when pump is NOT paired (BtAddress is empty)
    PairWithPump(
        key = "combov2_pair_with_pump",
        titleResId = R.string.combov2_pair_with_pump_title,
        enabledCondition = PreferenceEnabledCondition { ctx ->
            ctx.preferences.get(ComboStringNonKey.BtAddress).isEmpty()
        }
    ),

    // Unpair button enabled when pump IS paired (BtAddress is not empty)
    UnpairPump(
        key = "combov2_unpair_pump",
        titleResId = R.string.combov2_unpair_pump_title,
        confirmationMessageResId = R.string.combov2_unpair_pump_summary,
        enabledCondition = PreferenceEnabledCondition { ctx ->
            ctx.preferences.get(ComboStringNonKey.BtAddress).isNotEmpty()
        }
    ),
}
