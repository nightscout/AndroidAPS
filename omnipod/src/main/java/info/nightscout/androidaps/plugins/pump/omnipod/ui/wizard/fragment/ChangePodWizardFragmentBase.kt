package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.navigation.fragment.findNavController
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.ChangePodWizardActivity
import kotlinx.android.synthetic.main.omnipod_change_pod_wizard_base_fragment.*
import kotlinx.android.synthetic.main.omnipod_change_pod_wizard_nav_buttons.*
import kotlinx.android.synthetic.main.omnipod_change_pod_wizard_progress_indication.*
import kotlin.math.roundToInt

abstract class ChangePodWizardFragmentBase : DaggerFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val baseView = inflater.inflate(R.layout.omnipod_change_pod_wizard_base_fragment, container, false)
        val contentView = baseView.findViewById<ViewStub>(R.id.omnipod_change_pod_wizard_base_fragment_content)
        contentView?.let {
            it.layoutResource = getLayoutId()
            it.inflate()
        }
        return baseView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        omnipod_change_pod_wizard_base_fragment_title.setText(getTitleId())

        val nextPage = getNextPageActionId()

        if (nextPage == null) {
            omnipod_change_pod_wizard_button_next.text = getString(R.string.omnipod_change_pod_wizard_button_finish)
            omnipod_change_pod_wizard_button_next.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.review_green, context?.theme))
        }

        updateProgressIndication()

        omnipod_change_pod_wizard_button_next.setOnClickListener {
            if (nextPage == null) {
                activity?.finish()
            } else {
                findNavController().navigate(nextPage)
            }
        }

        omnipod_change_pod_wizard_button_cancel.setOnClickListener {
            (activity as? ChangePodWizardActivity)?.exitActivityAfterConfirmation()
        }
    }

    private fun updateProgressIndication() {
        val findNavController = findNavController()
        val graph = findNavController.graph
        val totalFragments =
            when (graph.startDestination) {
                R.id.fillPodInfoFragment   -> {
                    8 - 3
                }

                R.id.attachPodInfoFragment -> {
                    8 - 5
                }

                else                       -> {
                    8
                }
            }

        val currentFragment = getIndex() - (8 - totalFragments)
        val progressPercentage = (currentFragment / totalFragments.toDouble() * 100).roundToInt()

        omnipod_change_pod_wizard_progress_indication.progress = progressPercentage
    }

    @LayoutRes
    protected abstract fun getLayoutId(): Int

    @IdRes
    protected abstract fun getNextPageActionId(): Int?

    @StringRes
    protected abstract fun getTitleId(): Int

    protected abstract fun getIndex(): Int
}