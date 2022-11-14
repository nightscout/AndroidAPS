package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.fragment.action

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.di.OmnipodPluginQualifier
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InsertCannulaViewModel
import javax.inject.Inject

class InsertCannulaFragment : PodActivationActionFragmentBase() {

    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: InsertCannulaViewModel by viewModels { viewModelFactory }
        this.viewModel = vm
    }

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_insertCannulaFragment_to_PodActivatedFragment

    override fun getIndex(): Int = 4
}