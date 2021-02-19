package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.fragment

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.dagger.OmnipodPluginQualifier
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.FillPodInfoViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.fragment.InfoFragmentBase
import javax.inject.Inject

class FillPodInfoFragment : InfoFragmentBase() {
    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: FillPodInfoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: FillPodInfoViewModel by viewModels { viewModelFactory }
        this.viewModel = vm
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_activation_wizard_fill_pod_title

    @StringRes
    override fun getTextId(): Int = viewModel.getTextId()

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_fillPodInfoFragment_to_initializePodActionFragment

    override fun getIndex(): Int = 1
}