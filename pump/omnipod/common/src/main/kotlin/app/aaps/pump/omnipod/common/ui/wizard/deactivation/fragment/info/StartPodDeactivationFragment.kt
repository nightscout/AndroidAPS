package app.aaps.pump.omnipod.common.ui.wizard.deactivation.fragment.info

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.di.OmnipodPluginQualifier
import app.aaps.pump.omnipod.common.ui.wizard.common.fragment.InfoFragmentBase
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.StartPodDeactivationViewModel
import javax.inject.Inject

class StartPodDeactivationFragment : InfoFragmentBase() {

    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: StartPodDeactivationViewModel by viewModels { viewModelFactory }
        this.viewModel = vm
    }

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_startPodDeactivationFragment_to_deactivatePodFragment

    override fun getIndex(): Int = 1
}