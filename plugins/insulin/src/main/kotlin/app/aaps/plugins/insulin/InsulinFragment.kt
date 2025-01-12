package app.aaps.plugins.insulin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.plugins.insulin.databinding.InsulinFragmentBinding
import dagger.android.support.DaggerFragment
import java.text.DecimalFormat
import javax.inject.Inject

class InsulinFragment : DaggerFragment() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var hardLimits: HardLimits
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var insulinPlugin: InsulinPlugin
    @Inject lateinit var uel: UserEntryLogger

    private var _binding: InsulinFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val currentInsulin
        get() = insulinPlugin.currentInsulin()

    private val textWatch = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            currentInsulin?. let {
                it.insulinLabel = binding.name.text.toString()
                it.setPeak(SafeParse.stringToInt(binding.peak.text))
                it.setDia(SafeParse.stringToDouble(binding.dia.text))
            }
            doEdit()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InsulinFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.insulinList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (insulinPlugin.isEdited) {
                activity?.let { activity ->
                    OKDialog.showConfirmation(
                        activity, rh.gs(R.string.do_you_want_switch_insulin),
                        {
                            insulinPlugin.currentInsulinIndex = position
                            insulinPlugin.isEdited = false
                            build()
                        }, null
                    )
                }
            } else {
                insulinPlugin.currentInsulinIndex = position
                build()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.name.text = activePlugin.activeInsulin.friendlyName
        binding.comment.text = activePlugin.activeInsulin.comment
        //binding.dia.text = rh.gs(app.aaps.core.ui.R.string.dia) + ":  " + rh.gs(app.aaps.core.ui.R.string.format_hours, activePlugin.activeInsulin.dia)
        binding.peak.setParams(activePlugin.activeInsulin.peak.toDouble(), hardLimits.minPeak(), hardLimits.maxPeak(), 1.0, DecimalFormat("0"), false, null, textWatch)
        binding.dia.setParams(activePlugin.activeInsulin.dia, hardLimits.minDia(), hardLimits.maxDia(), 0.1, DecimalFormat("0.0"), false, null, textWatch)
        binding.graph.show(activePlugin.activeInsulin)
        build()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateGUI() {
        if (_binding == null) return
        val isValid = insulinPlugin.isValidEditState(activity)
        val isEdited = insulinPlugin.isEdited

        val insulinList: ArrayList<CharSequence> = insulinPlugin.insulinList()
        context?.let { context ->
            binding.insulinList.setAdapter(ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, insulinList))
        } ?: return
        binding.insulinList.setText(currentInsulin?.let { it.insulinLabel } ?:"", false)

        if (isValid) {
            this.view?.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.okBackgroundColor))
            binding.insulinList.isEnabled = true

            if (isEdited) {
                //edited insulin -> save first
                binding.updateProfiles.visibility = View.GONE
                binding.save.visibility = View.VISIBLE
            } else {
                binding.updateProfiles.visibility = View.VISIBLE
                binding.save.visibility = View.GONE
            }
        } else {
            this.view?.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.errorBackgroundColor))
            binding.insulinList.isEnabled = false
            binding.updateProfiles.visibility = View.GONE
            binding.save.visibility = View.GONE //don't save an invalid profile
        }

        //Show reset button if data was edited
        if (isEdited) {
            binding.reset.visibility = View.VISIBLE
        } else {
            binding.reset.visibility = View.GONE
        }
    }

    fun build() {
        val currentInsulin = insulinPlugin.currentInsulin() ?: return
        binding.name.removeTextChangedListener(textWatch)
        binding.name.setText(currentInsulin.insulinLabel)
        binding.name.addTextChangedListener(textWatch)
        binding.insulinList.filters = arrayOf()
        binding.insulinList.setText(currentInsulin.insulinLabel)

        binding.peak.setParams(currentInsulin.getPeak().toDouble(), hardLimits.minPeak(), hardLimits.maxPeak(), 1.0, DecimalFormat("0"), false, null, textWatch)
        binding.dia.setParams(currentInsulin.getDia(), hardLimits.minDia(), hardLimits.maxDia(), 0.1, DecimalFormat("0.0"), false, null, textWatch)
        binding.graph.show(activePlugin.activeInsulin)

        binding.reset.setOnClickListener {
            insulinPlugin.loadSettings()
            build()
        }
        binding.save.setOnClickListener {
            if (!insulinPlugin.isValidEditState(activity)) {
                return@setOnClickListener  //Should not happen as saveButton should not be visible if not valid
            }
            uel.log(
                action = Action.STORE_INSULIN, source = Sources.Insulin,
                value = ValueWithUnit.SimpleString(insulinPlugin.currentInsulin()?.insulinLabel ?: "")
            )
            insulinPlugin.storeSettings()
            build()
        }
        updateGUI()
    }

    fun doEdit() {
        insulinPlugin.isEdited = true
        updateGUI()
    }
}