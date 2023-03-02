package info.nightscout.pump.medtrum.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import info.nightscout.pump.medtrum.databinding.MedtrumOverviewFragmentBinding
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumOverviewViewModel
import info.nightscout.pump.medtrum.R
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class MedtrumOverviewFragment : MedtrumBaseFragment<MedtrumOverviewFragmentBinding>() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    private lateinit var resultLauncherForResume: ActivityResultLauncher<Intent>
    private lateinit var resultLauncherForPause: ActivityResultLauncher<Intent>

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun getLayoutId(): Int = R.layout.medtrum_overview_fragment

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            viewmodel = ViewModelProvider(this@MedtrumOverviewFragment, viewModelFactory).get(MedtrumOverviewViewModel::class.java)
            viewmodel?.apply {
                // TODO Handle events here, see eopatch eventhandler
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

    override fun onPause() {
        super.onPause()
        // TODO
    }

    override fun onResume() {
        super.onResume()
        // TODO
    }
}