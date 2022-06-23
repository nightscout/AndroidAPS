package info.nightscout.androidaps.interaction.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActionEditplusminusItemBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemQuickleftyPlusBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemQuickleftyBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemQuickrightyBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemQuickrightyPlusBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemViktoriaBinding
import info.nightscout.shared.sharedPreferences.SP

/**
 * NumberPickerViewAdapter binds both NumberPickerLayoutBinding and NumberPickerLayoutVerticalBinding shared attributes to one common view adapter.
 * Requires at least one of the ViewBinding as a parameter. Recommended to use the factory object to create the binding.
 */
class EditPlusMinusViewAdapter(
    val eS: ActionEditplusminusItemBinding?,
    val eQLP: ActionEditplusminusItemQuickleftyPlusBinding?,
    val eQL: ActionEditplusminusItemQuickleftyBinding?,
    val eQRP: ActionEditplusminusItemQuickleftyPlusBinding?,
    val qQR: ActionEditplusminusItemQuickrightyBinding?,
    val nQRP: ActionEditplusminusItemQuickrightyPlusBinding?,
    val eV: ActionEditplusminusItemViktoriaBinding?
) {

    init {
        if (eS == null && eQLP == null && eQL == null && eQRP == null && qQR == null && nQRP == null && eV == null) {
            throw IllegalArgumentException("Require at least on Binding parameter")
        }
    }

    val amountField =
        eS?.amountfield ?: eQLP?.amountfield ?: eQL?.amountfield ?: eQRP?.amountfield ?: qQR?.amountfield ?: nQRP?.amountfield ?: eV?.amountfield
        ?: throw IllegalArgumentException("Missing require View Binding parameter display")
    val minusButton =
        eS?.minusbutton ?: eQLP?.minusbutton ?: eQL?.minusbutton ?: eQRP?.minusbutton ?: qQR?.minusbutton ?: nQRP?.minusbutton ?: eV?.minusbutton
        ?: throw IllegalArgumentException("Missing require View Binding parameter display")
    val plusButton =
        eS?.plusbutton ?: eQLP?.plusbutton ?: eQL?.plusbutton ?: eQRP?.plusbutton ?: qQR?.plusbutton ?: nQRP?.plusbutton ?: eV?.plusbutton
        ?: throw IllegalArgumentException("Missing require View Binding parameter display")
    val label =
        eS?.label ?: eQLP?.label ?: eQL?.label ?: eQRP?.label ?: qQR?.label ?: nQRP?.label ?: eV?.label
        ?: throw IllegalArgumentException("Missing require View Binding parameter display")
    val plusButton2 = eQLP?.plusbutton2 ?: eQRP?.plusbutton2
    val plusButton3 = eQLP?.plusbutton3 ?: eQRP?.plusbutton3
    val root =
        eS?.root ?: eQLP?.root ?: eQL?.root ?: eQRP?.root ?: qQR?.root ?: nQRP?.root ?: eV?.root
        ?: throw IllegalArgumentException("Missing require View Binding parameter display")

    companion object {

        fun getInflatedPlusMinusView(sp: SP, context: Context, container: ViewGroup?, mulitple: Boolean = false): EditPlusMinusViewAdapter {
            val inflater = LayoutInflater.from(context)
            val bindLayout = ActionEditplusminusItemBinding.inflate(inflater, container, false)
            val binding = EditPlusMinusViewAdapter(bindLayout, null, null, null, null, null, null)
            return binding
            // val layoutRight = if (mulitple) R.layout.action_editplusminus_item_quickrighty_plus else R.layout.action_editplusminus_item_quickrighty
            // val layoutLeft = if (mulitple) R.layout.action_editplusminus_item_quicklefty_plus else R.layout.action_editplusminus_item_quicklefty
            // return when (sp.getInt(R.string.key_input_design, 1)) {
            //     2    -> LayoutInflater.from(applicationContext).inflate(layoutRight, container, false)
            //     3    -> LayoutInflater.from(applicationContext).inflate(layoutLeft, container, false)
            //     4    -> LayoutInflater.from(applicationContext).inflate(R.layout.action_editplusminus_item_viktoria, container, false)
            //     else -> LayoutInflater.from(applicationContext).inflate(R.layout.action_editplusminus_item, container, false)
            // }
        }
        // fun getBinding(bindLayout: NumberPickerLayoutBinding): NumberPickerViewAdapter {
        //     return NumberPickerViewAdapter(bindLayout, null)
        // }
        //
        // fun getBinding(bindLayout: NumberPickerLayoutVerticalBinding): NumberPickerViewAdapter {
        //     return NumberPickerViewAdapter(null, bindLayout)
        // }
    }
}