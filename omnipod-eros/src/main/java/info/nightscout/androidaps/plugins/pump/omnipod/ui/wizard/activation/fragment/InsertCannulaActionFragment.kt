package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.fragment

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.dagger.OmnipodPluginQualifier
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.viewmodel.InsertCannulaActionViewModel
import javax.inject.Inject

class InsertCannulaActionFragment : PodActivationActionFragmentBase() {

    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: InsertCannulaActionViewModel by viewModels { viewModelFactory }
        this.viewModel = vm
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_pod_activation_wizard_insert_cannula_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_pod_activation_wizard_insert_cannula_text

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_insertCannulaActionFragment_to_PodActivatedInfoFragment

    override fun getIndex(): Int = 4
}