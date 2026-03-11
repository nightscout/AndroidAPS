package app.aaps.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.EventType
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import app.aaps.pump.medtrum.databinding.FragmentMedtrumOverviewBinding
import app.aaps.pump.medtrum.ui.viewmodel.MedtrumOverviewViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class MedtrumOverviewFragment : MedtrumBaseFragment<FragmentMedtrumOverviewBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var medtrumPump: MedtrumPump
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var rh: ResourceHelper

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_overview

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            viewmodel = ViewModelProvider(this@MedtrumOverviewFragment, viewModelFactory)[MedtrumOverviewViewModel::class.java]
            viewmodel?.apply {
                eventHandler.observe(viewLifecycleOwner) { evt ->
                    when (evt.peekContent()) {
                        EventType.CHANGE_PATCH_CLICKED -> requireContext().apply {
                            protectionCheck.queryProtection(
                                requireActivity(),
                                ProtectionCheck.Protection.PREFERENCES,
                                {
                                    val nextStep = when {
                                        medtrumPump.pumpState > MedtrumPumpState.EJECTED && medtrumPump.pumpState < MedtrumPumpState.STOPPED -> {
                                            PatchStep.START_DEACTIVATION
                                        }

                                        medtrumPump.pumpState in listOf(MedtrumPumpState.STOPPED, MedtrumPumpState.NONE)                     -> {
                                            PatchStep.PREPARE_PATCH
                                        }

                                        else                                                                                                 -> {
                                            PatchStep.RETRY_ACTIVATION
                                        }
                                    }
                                    startActivity(MedtrumActivity.createIntentFromMenu(this, nextStep))
                                }
                            )
                        }

                        EventType.PROFILE_NOT_SET      -> {
                            OKDialog.show(requireActivity(), rh.gs(app.aaps.core.ui.R.string.message), rh.gs(R.string.no_profile_selected))
                        }

                        EventType.SERIAL_NOT_SET       -> {
                            OKDialog.show(requireActivity(), rh.gs(app.aaps.core.ui.R.string.message), rh.gs(R.string.no_sn_in_settings))
                        }
                    }
                }
            }
        }

        // Set pump themed state colors
        medtrumPump.setStateColors(
            rh.gac(view.context, app.aaps.core.ui.R.attr.defaultTextColor),
            rh.gac(view.context, app.aaps.core.ui.R.attr.highColor),
            rh.gac(view.context, app.aaps.core.ui.R.attr.lowColor)
        )
    }
}
