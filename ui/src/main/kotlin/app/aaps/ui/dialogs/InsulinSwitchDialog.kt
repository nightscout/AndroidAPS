package app.aaps.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.objects.extensions.fromJson
import app.aaps.core.objects.extensions.toJson
import app.aaps.core.ui.R
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.ui.databinding.DialogInsulinswitchBinding
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class InsulinSwitchDialog : DialogFragmentWithDate() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uiInteraction: UiInteraction

    var helperActivity: TranslatedDaggerAppCompatActivity? = null
    private var _binding: DialogInsulinswitchBinding? = null
    private var iCfg: ICfg? = null
    private var concentration: Double? = null
    private var insulinList: List<CharSequence> = emptyList()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putString("iCfg", iCfg?.toJson().toString())
        concentration?.let { savedInstanceState.putDouble("concentration", it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        arguments?.let { bundle ->
            iCfg = bundle.getString("iCfg", null)?.let { ICfg.fromJson(JSONObject(it))}
            if (bundle.containsKey("concentration"))
                concentration = bundle.getDouble("concentration")
        }
        _binding = DialogInsulinswitchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.okcancel.ok.text = rh.gs(R.string.next)
        iCfg = iCfg ?: activePlugin.activeInsulin.getDefaultInsulin(concentration)
        updateFieldText()
        iCfg?.let { iCfg ->
            context?.let { context ->
                insulinList = activePlugin.activeInsulin.insulinList(concentration)
                binding.insulinList.setAdapter(ArrayAdapter(context, R.layout.spinner_centered, insulinList))
                binding.insulinList.setText(iCfg.insulinLabel, false)
            }
        }
        binding.insulinList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            iCfg = activePlugin.activeInsulin.getInsulin(insulinList[position].toString())
            updateFieldText()
        }
    }

    private fun updateFieldText() {
        iCfg?.let { iCfg ->
            binding.concentration.text = rh.gs(ConcentrationType.fromDouble(iCfg.concentration).label)
            binding.peak.text = rh.gs(R.string.format_mins, iCfg.peak)
            binding.dia.text = rh.gs(R.string.format_hours, iCfg.dia)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun dismiss() {
        super.dismissAllowingStateLoss()
        helperActivity?.finish()
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        uiInteraction.runProfileSwitchDialog(parentFragmentManager, iCfg = iCfg)
        return true
    }
}
