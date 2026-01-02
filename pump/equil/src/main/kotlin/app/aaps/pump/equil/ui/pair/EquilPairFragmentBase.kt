package app.aaps.pump.equil.ui.pair

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.navigation.fragment.findNavController
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.databinding.EquilPairBaseFragmentBinding
import app.aaps.pump.equil.databinding.EquilPairProgressBinding
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.ui.dlg.LoadingDlg
import dagger.android.support.DaggerFragment
import javax.inject.Inject
import kotlin.math.roundToInt

abstract class EquilPairFragmentBase : DaggerFragment() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin
    @Inject lateinit var equilManager: EquilManager
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var equilHistoryRecordDao: EquilHistoryRecordDao
    @Inject lateinit var constraintsChecker: ConstraintsChecker

    private var _binding: EquilPairBaseFragmentBinding? = null
    private var _progressIndicationBinding: EquilPairProgressBinding? = null
    val binding get() = _binding!!
    private val progressIndicationBinding get() = _progressIndicationBinding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        EquilPairBaseFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            _progressIndicationBinding = EquilPairProgressBinding.bind(it.root)

            it.fragmentContent.layoutResource = getLayoutId()
            it.fragmentContent.inflate()
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nextPage = getNextPageActionId()

        if (nextPage == null) {
            binding.navButtonsLayout.buttonNext.text = getString(R.string.equil_common_wizard_button_finish)
            binding.navButtonsLayout.buttonNext.backgroundTintList =
                ColorStateList.valueOf(rh.gc(R.color.equilWizardFinishButtonColor))
        }

        updateProgressIndication()

        binding.navButtonsLayout.buttonNext.setOnClickListener {
            if (nextPage == null) {
                activity?.finish()
            } else {
                if (isAdded)
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
        _progressIndicationBinding = null
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

    protected abstract fun getIndex(): Int

    protected fun showLoading() {
        if (activity == null || !isAdded) return
        LoadingDlg().show(childFragmentManager, "loading")
    }

    protected fun dismissLoading() {
        if (activity == null || !isAdded) return
        val fragment = childFragmentManager.findFragmentByTag("loading")
        if (fragment is LoadingDlg) {
            try {
                fragment.dismiss()
            } catch (e: IllegalStateException) {
                // dialog not running yet
                aapsLogger.error("Unhandled exception", e)
            }
        }
    }
}