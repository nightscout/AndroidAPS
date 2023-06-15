package info.nightscout.pump.medtrum.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import info.nightscout.rx.logging.LTag
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class MedtrumOverviewFragment : MedtrumBaseFragment<FragmentMedtrumOverviewBinding>() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var medtrumPump: MedtrumPump
    private lateinit var resultLauncherForResume: ActivityResultLauncher<Intent>
    private lateinit var resultLauncherForPause: ActivityResultLauncher<Intent>

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_overview

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            viewmodel = ViewModelProvider(this@MedtrumOverviewFragment, viewModelFactory).get(MedtrumOverviewViewModel::class.java)
            viewmodel?.apply {
                eventHandler.observe(viewLifecycleOwner) { evt ->
                    when (evt.peekContent()) {
                        EventType.CHANGE_PATCH_CLICKED -> requireContext().apply {
                            if (medtrumPump.pumpState > MedtrumPumpState.EJECTED && medtrumPump.pumpState < MedtrumPumpState.STOPPED) {
                                startActivity(MedtrumActivity.createIntentFromMenu(this, PatchStep.START_DEACTIVATION))
                            } else {
                                startActivity(MedtrumActivity.createIntentFromMenu(this, PatchStep.PREPARE_PATCH))
                            }
                        }
                        else                           -> Unit
                    }
                }

                resultLauncherForResume = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    when (it.resultCode) {
                        // TODO Handle events here, see eopatch eventhandler
                    }
                }

                resultLauncherForPause = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    when (it.resultCode) {
                        // TODO Handle events here, see eopatch eventhandler
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // TODO
    }

    override fun onResume() {
        super.onResume()
        // TODO
    }
}