package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.navigation.fragment.findNavController
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.ReplacePodWizardActivity
import kotlinx.android.synthetic.main.omnipod_replace_pod_wizard_base_fragment.*
import kotlinx.android.synthetic.main.omnipod_replace_pod_wizard_nav_buttons.*
import kotlinx.android.synthetic.main.omnipod_replace_pod_wizard_progress_indication.*
import kotlin.math.roundToInt

abstract class FragmentBase : DaggerFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val baseView = inflater.inflate(R.layout.omnipod_replace_pod_wizard_base_fragment, container, false)
        val contentView = baseView.findViewById<ViewStub>(R.id.omnipod_wizard_base_fragment_content)
        contentView?.let {
            it.layoutResource = getLayoutId()
            it.inflate()
        }
        return baseView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        omnipod_wizard_base_fragment_title.text = getTitle()

        val nextPage = getNextPageActionId()

        if (nextPage == null) {
            omnipod_replace_pod_wizard_button_next.text = getString(R.string.omnipod_replace_pod_wizard_button_finish)
            omnipod_replace_pod_wizard_button_next.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.review_green, context?.theme))
        }

        updateProgressIndication()

        omnipod_replace_pod_wizard_button_next.setOnClickListener {
            if (nextPage == null) {
                activity?.finish()
            } else {
                findNavController().navigate(nextPage)
            }
        }

        omnipod_replace_pod_wizard_button_cancel.setOnClickListener {
            (activity as? ReplacePodWizardActivity)?.exitActivityAfterConfirmation()
        }
    }

    private fun updateProgressIndication() {
        val totalFragments =
            when (findNavController().graph.startDestination) {
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

        omnipod_replace_pod_wizard_progress_indication.progress = progressPercentage
    }

    @LayoutRes
    abstract fun getLayoutId(): Int

    @IdRes
    abstract fun getNextPageActionId(): Int?

    abstract fun getTitle(): String

    abstract fun getIndex(): Int
}