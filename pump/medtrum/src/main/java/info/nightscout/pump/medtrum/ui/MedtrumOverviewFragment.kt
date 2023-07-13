package info.nightscout.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumOverviewBinding
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumOverviewViewModel
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.EventType
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class MedtrumOverviewFragment : MedtrumBaseFragment<FragmentMedtrumOverviewBinding>() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var medtrumPump: MedtrumPump

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_overview

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            viewmodel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumOverviewViewModel::class.java]
            viewmodel?.apply {
                eventHandler.observe(viewLifecycleOwner) { evt ->
                    when (evt.peekContent()) {
                        EventType.CHANGE_PATCH_CLICKED -> requireContext().apply {
                            if (medtrumPump.pumpState > MedtrumPumpState.EJECTED && medtrumPump.pumpState < MedtrumPumpState.STOPPED) {
                                startActivity(MedtrumActivity.createIntentFromMenu(this, PatchStep.START_DEACTIVATION))
                            } else if (medtrumPump.pumpState in listOf(MedtrumPumpState.STOPPED, MedtrumPumpState.NONE)) {
                                startActivity(MedtrumActivity.createIntentFromMenu(this, PatchStep.PREPARE_PATCH))
                            } else {
                                startActivity(MedtrumActivity.createIntentFromMenu(this, PatchStep.RETRY_ACTIVATION))
                            }
                        }
                        else                           -> Unit
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }
}