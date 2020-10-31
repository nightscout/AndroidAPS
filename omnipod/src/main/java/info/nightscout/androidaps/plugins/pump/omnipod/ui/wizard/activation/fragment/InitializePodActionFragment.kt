package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.fragment

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.dagger.OmnipodPluginQualifier
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.viewmodel.InitializePodActionViewModel
import javax.inject.Inject

class InitializePodActionFragment : PodActivationActionFragmentBase() {
    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: InitializePodActionViewModel by viewModels { viewModelFactory }
        this.viewModel = vm
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_pod_activation_wizard_initialize_pod_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_pod_activation_wizard_initialize_pod_text

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_initializePodActionFragment_to_attachPodInfoFragment

    override fun getIndex(): Int = 2
}