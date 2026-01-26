package app.aaps.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConcentrationChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.DoubleNonKey
import app.aaps.core.ui.R
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.ui.activities.ConcentrationActivity
import app.aaps.ui.databinding.DialogConcentrationBinding
import dagger.android.HasAndroidInjector
import java.text.DecimalFormat
import javax.inject.Inject

class ConcentrationDialog : DialogFragmentWithDate() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uiInteraction: UiInteraction

    var helperActivity: TranslatedDaggerAppCompatActivity? = null
    private var _binding: DialogConcentrationBinding? = null
    private val currentConcentration: Double
        get()= preferences.get(DoubleNonKey.ApprovedConcentration)
    private val targetConcentration: Double
        get()= preferences.get(DoubleNonKey.NewConcentration)
    private val confirmOnly: Boolean
        get() = currentConcentration == targetConcentration && activePlugin.activeInsulin.iCfg.concentration == targetConcentration
    private val currentConcentrationString: String
        get() = rh.gs(ConcentrationType.fromDouble(currentConcentration).label)
    private val targetConcentrationString: String
        get() = rh.gs(ConcentrationType.fromDouble(targetConcentration).label)
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("concentration", binding.concentration.value.toInt())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogConcentrationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.concentration.setParams(
            0.0, 40.0, 200.0, 10.0, DecimalFormat("0"), false, binding.okcancel.ok
        )
        if (confirmOnly)
            binding.message.text = rh.gs(R.string.concentration_title, currentConcentrationString)
        else {
            binding.okcancel.ok.text = rh.gs(R.string.next)
            binding.message.text = rh.gs(R.string.concentration_title2, currentConcentrationString, targetConcentrationString)
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
        val userConcentration = binding.concentration.value
        val concentration = userConcentration / 100.0
        when (concentration) {
            targetConcentration -> { // default,either for a concentration change or confirmation, it should be here
                activity?.let { activity ->
                    if (confirmOnly) {  // Ok to confirm new concentration
                        uiInteraction.showOkCancelDialog(
                            context = activity,
                            title = rh.gs(R.string.concentration),
                            message = rh.gs(R.string.ins_concentration_confirmed, targetConcentrationString),
                            ok = {
                                uel.log(action = Action.RESERVOIR_CHANGE, source = Sources.ConcentrationDialog, value = ValueWithUnit.InsulinConcentration(userConcentration.toInt()))
                                activePlugin.activeInsulin.approveConcentration(targetConcentration)
                                rxBus.send(EventConcentrationChange())
                                if (activity is ConcentrationActivity)
                                    activity.finish()
                            },
                            cancel = {
                                if (activity is ConcentrationActivity)
                                    activity.finish()
                            }
                        )
                    } else {    // next to apply new concentration after insulin selection and profileswitch
                        uiInteraction.runInsulinSwitchDialog(parentFragmentManager, concentration = concentration)
                    }
                }
            }
            else                -> {
                activity?.let { activity ->
                    uiInteraction.showOkDialog(activity, rh.gs(R.string.concentration), rh.gs(R.string.concentration_not_confirmed))
                }
            }
        }
        return true
    }
}
