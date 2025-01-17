package app.aaps.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.databinding.FragmentMedtrumActivateCompleteBinding
import app.aaps.pump.medtrum.ui.viewmodel.MedtrumViewModel
import javax.inject.Inject

class MedtrumActivateCompleteFragment : MedtrumBaseFragment<FragmentMedtrumActivateCompleteBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var importExportPrefs: ImportExportPrefs

    companion object {

        fun newInstance(): MedtrumActivateCompleteFragment = MedtrumActivateCompleteFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_activate_complete

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        MedtrumViewModel.SetupStep.INITIAL,
                        MedtrumViewModel.SetupStep.PRIMED    -> Unit // Nothing to do here, previous state
                        MedtrumViewModel.SetupStep.ACTIVATED -> btnPositive.visibility = View.VISIBLE

                        else                                 -> {
                            ToastUtils.errorToast(requireContext(), rh.gs(R.string.unexpected_state, it.toString()))
                            aapsLogger.error(LTag.PUMP, "Unexpected state: $it")
                        }
                    }
                }
            }
        }
        binding.navExport.setOnClickListener {
            uel.log(Action.EXPORT_SETTINGS, Sources.Maintenance)
            // start activity for checking permissions...
            importExportPrefs.verifyStoragePermissions(this) {
                importExportPrefs.exportSharedPreferences(this)
            }
        }
    }
}
