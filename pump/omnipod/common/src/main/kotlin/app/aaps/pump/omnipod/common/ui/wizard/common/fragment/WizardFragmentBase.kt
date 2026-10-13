package app.aaps.pump.omnipod.common.ui.wizard.common.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.navigation.fragment.findNavController
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.databinding.OmnipodCommonWizardBaseFragmentBinding
import app.aaps.pump.omnipod.common.databinding.OmnipodCommonWizardProgressIndicationBinding
import app.aaps.pump.omnipod.common.ui.wizard.common.activity.OmnipodWizardActivityBase
import app.aaps.pump.omnipod.common.ui.wizard.common.viewmodel.ViewModelBase
import dagger.android.support.DaggerFragment
import javax.inject.Inject
import kotlin.math.roundToInt

abstract class WizardFragmentBase : DaggerFragment() {

    protected lateinit var viewModel: ViewModelBase
    @Inject lateinit var rh: ResourceHelper

    var _binding: OmnipodCommonWizardBaseFragmentBinding? = null
    var _progressIndicationBinding: OmnipodCommonWizardProgressIndicationBinding? = null

    // These properties are only valid between onCreateView and
    // onDestroyView.
    val binding get() = _binding!!
    val progressIndicationBinding get() = _progressIndicationBinding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OmnipodCommonWizardBaseFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            _progressIndicationBinding = OmnipodCommonWizardProgressIndicationBinding.bind(it.root)

            it.fragmentContent.layoutResource = getLayoutId()
            it.fragmentContent.inflate()
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fragmentTitle.setText(getTitleId())

        val nextPage = getNextPageActionId()

        if (nextPage == null) {
            binding.navButtonsLayout.buttonNext.text = getString(R.string.omnipod_common_wizard_button_finish)
            binding.navButtonsLayout.buttonNext.backgroundTintList =
                ColorStateList.valueOf(rh.gac(context, app.aaps.core.ui.R.attr.omniWizardFinishButtonColor))
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
            (activity as? OmnipodWizardActivityBase)?.exitActivityAfterConfirmation()
        }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateProgressIndication() {
        (activity as? OmnipodWizardActivityBase)?.let {
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

    @StringRes
    protected fun getTitleId(): Int = viewModel.getTitleId()

    @StringRes protected fun getTextId(): Int = viewModel.getTextId()

    protected abstract fun getIndex(): Int
}