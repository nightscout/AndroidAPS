package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.dagger.OmnipodPluginQualifier
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.viewmodel.WizardViewModel1
import javax.inject.Inject

class WizardFragment1 : DaggerFragment() {

    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var viewModel: WizardViewModel1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: WizardViewModel1 by viewModels<WizardViewModel1> { viewModelFactory }
        this.viewModel = vm
        vm.doSomethingForTesting()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.omnipod_replace_pod_wizard_info_page_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //view.findViewById<Button>(R.id.test_button)?.setOnClickListener {
        //    viewModel.onButtonPressedForTesting()
        //}
    }

}