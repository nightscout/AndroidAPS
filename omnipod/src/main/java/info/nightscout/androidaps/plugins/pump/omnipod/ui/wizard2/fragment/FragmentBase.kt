package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.navigation.fragment.findNavController
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.ReplacePodWizardActivity

abstract class FragmentBase : DaggerFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(getLayout(), container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.omnipod_replace_pod_wizard_button_cancel)?.setOnClickListener {
            activity?.finish()
        }

        val nextButton = view.findViewById<Button>(R.id.omnipod_replace_pod_wizard_button_next)
        nextButton?.let {
            val nextPage = getNextPageActionId()

            it.text = if (nextPage == null) {
                getString(R.string.omnipod_replace_pod_wizard_button_finish)
            } else {
                getString(R.string.omnipod_replace_pod_wizard_button_next)
            }
            it.setOnClickListener {
                if (nextPage == null) {
                    activity?.finish()
                } else {
                    findNavController().navigate(nextPage)
                }
            }
        }

        val startDestination = (activity as ReplacePodWizardActivity).startDestination
        val total =
            when (startDestination) {
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

        // TODO
        view.findViewById<TextView>(R.id.omnipod_replace_pod_wizard_progress_indication)?.text = "X/$total"
    }

    @LayoutRes
    abstract fun getLayout(): Int

    @IdRes
    abstract fun getNextPageActionId(): Int?
}