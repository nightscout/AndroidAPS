package app.aaps.wear.interaction.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.databinding.ActionEditplusminBinding
import app.aaps.wear.databinding.ActionEditplusminMultiBinding
import app.aaps.wear.databinding.ActionEditplusminQuickleftyBinding
import app.aaps.wear.databinding.ActionEditplusminQuickleftyMultiBinding
import app.aaps.wear.databinding.ActionEditplusminQuickrightyBinding
import app.aaps.wear.databinding.ActionEditplusminQuickrightyMultiBinding
import app.aaps.wear.databinding.ActionEditplusminViktoriaBinding

/**
 * EditPlusMinusViewAdapter binds both ActionEditplusminBinding variants shared attributes to one common view adapter.
 * Requires at least one of the ViewBinding as a parameter. Recommended to use the factory object to create the binding.
 */
class EditPlusMinusViewAdapter(
    eD: ActionEditplusminBinding?,
    eDP: ActionEditplusminMultiBinding?,
    eQL: ActionEditplusminQuickleftyBinding?,
    eQLP: ActionEditplusminQuickleftyMultiBinding?,
    eQR: ActionEditplusminQuickrightyBinding?,
    eQRP: ActionEditplusminQuickrightyMultiBinding?,
    eV: ActionEditplusminViktoriaBinding?
) {

    init {
        if (eD == null && eDP == null && eQL == null && eQLP == null && eQR == null && eQRP == null && eV == null) {
            throw IllegalArgumentException("Require at least on Binding parameter")
        }
    }

    private val errorMessage = "Missing require View Binding parameter"
    val editText =
        eD?.editText ?: eDP?.editText ?: eQL?.editText ?: eQLP?.editText ?: eQR?.editText ?: eQRP?.editText ?: eV?.editText
        ?: throw IllegalArgumentException(errorMessage)
    val minButton =
        eD?.minButton ?: eDP?.minButton ?: eQL?.minButton ?: eQLP?.minButton ?: eQR?.minButton ?: eQRP?.minButton ?: eV?.minButton
        ?: throw IllegalArgumentException(errorMessage)
    val plusButton1 =
        eD?.plusButton1 ?: eDP?.plusButton1 ?: eQL?.plusButton1 ?: eQLP?.plusButton1 ?: eQR?.plusButton1 ?: eQRP?.plusButton1 ?: eV?.plusButton1
        ?: throw IllegalArgumentException(errorMessage)
    val label =
        eD?.label ?: eDP?.label ?: eQL?.label ?: eQLP?.label ?: eQR?.label ?: eQRP?.label ?: eV?.label
        ?: throw IllegalArgumentException(errorMessage)
    val plusButton2 = eDP?.plusButton2 ?: eQLP?.plusButton2 ?: eQRP?.plusButton2
    val plusButton3 = eDP?.plusButton3 ?: eQLP?.plusButton3 ?: eQRP?.plusButton3
    val root =
        eD?.root ?: eDP?.root ?: eQL?.root ?: eQLP?.root ?: eQR?.root ?: eQRP?.root ?: eV?.root
        ?: throw IllegalArgumentException(errorMessage)

    companion object {

        fun getViewAdapter(sp: SP, context: Context, container: ViewGroup, multiple: Boolean = false): EditPlusMinusViewAdapter {
            val inflater = LayoutInflater.from(context)

            return when (sp.getInt(R.string.key_input_design, 1)) {
                2    -> {
                    if (multiple) {
                        val bindLayout = ActionEditplusminQuickrightyMultiBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(null, null, null, null, null, bindLayout, null)
                    } else {
                        val bindLayout = ActionEditplusminQuickrightyBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(null, null, null, null, bindLayout, null, null)
                    }
                }

                3    -> {
                    if (multiple) {
                        val bindLayout = ActionEditplusminQuickleftyMultiBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(null, null, null, bindLayout, null, null, null)
                    } else {
                        val bindLayout = ActionEditplusminQuickleftyBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(null, null, bindLayout, null, null, null, null)
                    }
                }

                4    -> {
                    val bindLayout = ActionEditplusminViktoriaBinding.inflate(inflater, container, false)
                    EditPlusMinusViewAdapter(null, null, null, null, null, null, bindLayout)
                }

                else -> {
                    if (multiple) {
                        val bindLayout = ActionEditplusminMultiBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(null, bindLayout, null, null, null, null, null)
                    } else {
                        val bindLayout = ActionEditplusminBinding.inflate(inflater, container, false)
                        EditPlusMinusViewAdapter(bindLayout, null, null, null, null, null, null)
                    }
                }
            }
        }
    }
}
