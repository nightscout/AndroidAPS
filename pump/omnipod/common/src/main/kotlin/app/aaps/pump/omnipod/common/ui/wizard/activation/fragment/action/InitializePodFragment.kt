package app.aaps.pump.omnipod.common.ui.wizard.activation.fragment.action

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.di.OmnipodPluginQualifier
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InitializePodViewModel
import javax.inject.Inject

class InitializePodFragment : PodActivationActionFragmentBase() {

    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: InitializePodViewModel by viewModels { viewModelFactory }
        this.viewModel = vm
    }

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_initializePodFragment_to_attachPodFragment

    override fun getIndex(): Int = 2
}