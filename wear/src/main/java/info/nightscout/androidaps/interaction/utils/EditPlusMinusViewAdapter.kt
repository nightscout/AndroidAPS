package info.nightscout.androidaps.interaction.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActionEditplusminusItemBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemPlusBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemQuickleftyPlusBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemQuickleftyBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemQuickrightyBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemQuickrightyPlusBinding
import info.nightscout.androidaps.databinding.ActionEditplusminusItemViktoriaBinding
import info.nightscout.shared.sharedPreferences.SP

/**
 * EditPlusMinusViewAdapter binds both ActionEditplusminusItemBinding variants shared attributes to one common view adapter.
 * Requires at least one of the ViewBinding as a parameter. Recommended to use the factory object to create the binding.
 */
class EditPlusMinusViewAdapter(
    eD: ActionEditplusminusItemBinding?,
    eDP: ActionEditplusminusItemPlusBinding?,
    eQL: ActionEditplusminusItemQuickleftyBinding?,
    eQLP: ActionEditplusminusItemQuickleftyPlusBinding?,
    eQR: ActionEditplusminusItemQuickrightyBinding?,
    eQRP: ActionEditplusminusItemQuickrightyPlusBinding?,
    eV: ActionEditplusminusItemViktoriaBinding?
) {

    init {
        if (eD == null && eDP == null && eQL == null && eQLP == null && eQR == null && eQRP == null && eV == null) {
            throw IllegalArgumentException("Require at least on Binding parameter")
        }
    }

    val editText =
        eD?.editText ?: eDP?.editText ?: eQL?.editText ?: eQLP?.editText ?: eQR?.editText ?: eQRP?.editText ?: eV?.editText
        ?: throw IllegalArgumentException("Missing require View Binding parameter display")
    val minButton =
        eD?.minButton ?: eDP?.minButton ?: eQL?.minButton ?: eQLP?.minButton ?: eQR?.minButton ?: eQRP?.minButton ?: eV?.minButton
        ?: throw IllegalArgumentException("Missing require View Binding parameter display")
    val plusButton1 =
        eD?.plusButton1 ?: eDP?.plusButton1 ?: eQL?.plusButton1 ?: eQLP?.plusButton1 ?: eQR?.plusButton1 ?: eQRP?.plusButton1 ?: eV?.plusButton1
        ?: throw IllegalArgumentException("Missing require View Binding parameter display")
    val label =
        eD?.label ?: eDP?.label ?: eQL?.label ?: eQLP?.label ?: eQR?.label ?: eQRP?.label ?: eV?.label
        ?: throw IllegalArgumentException("Missing require View Binding parameter display")
    val plusButton2 = eDP?.plusButton2 ?: eQLP?.plusButton2 ?: eQRP?.plusButton2
    val plusButton3 = eDP?.plusButton3 ?: eQLP?.plusButton3 ?: eQRP?.plusButton3
    val root =
        eD?.root ?: eDP?.root ?: eQL?.root ?: eQLP?.root ?: eQR?.root ?: eQRP?.root ?: eV?.root
        ?: throw IllegalArgumentException("Missing require View Binding parameter display")

    companion object {

        fun getViewAdapter(sp: SP, context: Context, container: ViewGroup?, multiple: Boolean = false): EditPlusMinusViewAdapter {
            val inflater = LayoutInflater.from(context)

            return when (sp.getInt(R.string.key_input_design, 1)) {
                2    -> {
                    if (multiple) {
                        val bindLayout = ActionEditplusminusItemQuickrightyPlusBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(null, null, null, null, null, bindLayout, null)
                    } else {
                        val bindLayout = ActionEditplusminusItemQuickrightyBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(null, null, null, null, bindLayout, null, null)
                    }
                }

                3    -> {
                    if (multiple) {
                        val bindLayout = ActionEditplusminusItemQuickleftyPlusBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(null, null, null, bindLayout, null, null, null)
                    } else {
                        val bindLayout = ActionEditplusminusItemQuickleftyBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(null, null, bindLayout, null, null, null, null)
                    }
                }

                4    -> {
                    val bindLayout = ActionEditplusminusItemViktoriaBinding.inflate(inflater, container, false)
                    EditPlusMinusViewAdapter(null, null, null, null, null, null, bindLayout)
                }

                else -> {
                    if (multiple) {
                        val bindLayout = ActionEditplusminusItemPlusBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(null, bindLayout, null, null, null, null, null)
                    } else {
                        val bindLayout = ActionEditplusminusItemBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(bindLayout, null, null, null, null, null, null)
                    }
                }
            }
        }
    }
}
