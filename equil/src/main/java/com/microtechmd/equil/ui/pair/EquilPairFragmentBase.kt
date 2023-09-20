package com.microtechmd.equil.ui.pair

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.microtechmd.equil.EquilPumpPlugin
import com.microtechmd.equil.databinding.EquilPairBaseFragmentBinding
import com.microtechmd.equil.databinding.EquilPairProgressBinding
import com.microtechmd.equil.ui.dlg.LoadingDlg
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.pump.common.ble.BlePreCheck
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.databinding.OmnipodCommonWizardBaseFragmentBinding
import info.nightscout.androidaps.plugins.pump.omnipod.common.databinding.OmnipodCommonWizardProgressIndicationBinding
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.activity.OmnipodWizardActivityBase
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import kotlin.math.roundToInt

abstract class EquilPairFragmentBase : DaggerFragment() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin

    var _binding: EquilPairBaseFragmentBinding? = null
    var _progressIndicationBinding: EquilPairProgressBinding? = null
    val binding get() = _binding!!
    val progressIndicationBinding get() = _progressIndicationBinding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        EquilPairBaseFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            _progressIndicationBinding = EquilPairProgressBinding.bind(it.root)

            it.fragmentContent.layoutResource = getLayoutId()
            it.fragmentContent.inflate()
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // binding.fragmentTitle.setText(getTitleId())

        val nextPage = getNextPageActionId()

        if (nextPage == null) {
            binding.navButtonsLayout.buttonNext.text = getString(R.string.omnipod_common_wizard_button_finish)
            binding.navButtonsLayout.buttonNext.backgroundTintList =
                ColorStateList.valueOf(rh.gac(context, R.attr.omniWizardFinishButtonColor))
        }

        updateProgressIndication()

        binding.navButtonsLayout.buttonNext.setOnClickListener {
            if (nextPage == null) {
                activity?.finish()
            } else {
                findNavController().navigate(nextPage)
            }
        }

        binding.navButtonsLayout.buttonCancel.setOnClickListener {
            (activity as? EquilPairActivity)?.exitActivityAfterConfirmation()
        }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateProgressIndication() {
        (activity as? EquilPairActivity)?.let {
            val numberOfSteps = it.getActualNumberOfSteps()

            val currentFragment = getIndex() - (it.getTotalDefinedNumberOfSteps() - numberOfSteps)
            val progressPercentage = (currentFragment / numberOfSteps.toDouble() * 100).roundToInt()

            progressIndicationBinding.progressIndication.progress = progressPercentage
        }
    }

    @LayoutRes
    protected abstract fun getLayoutId(): Int

    @IdRes
    protected abstract fun getNextPageActionId(): Int?

    // @StringRes
    // protected fun getTitleId(): Int = viewModel.getTitleId()
    //
    // @StringRes protected fun getTextId(): Int = viewModel.getTextId()

    protected abstract fun getIndex(): Int

    protected fun showLoading() {
        LoadingDlg().also { dialog ->
        }.show(childFragmentManager, "loading")
    }

    protected fun dismissLoading() {
        val fragment = childFragmentManager.findFragmentByTag("loading")
        if (fragment is LoadingDlg) {
            fragment.dismiss()
        }
    }
}