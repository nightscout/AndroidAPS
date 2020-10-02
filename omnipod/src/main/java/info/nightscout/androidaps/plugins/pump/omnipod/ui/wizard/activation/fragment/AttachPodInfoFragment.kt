package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.navigation.fragment.findNavController
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.common.fragment.InfoFragmentBase
import kotlinx.android.synthetic.main.omnipod_wizard_nav_buttons.*

class AttachPodInfoFragment : InfoFragmentBase() {
    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_pod_activation_wizard_attach_pod_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_pod_activation_wizard_attach_pod_text

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_attachPodInfoFragment_to_insertCannulaActionFragment

    override fun getIndex(): Int = 3

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        omnipod_wizard_button_next.setOnClickListener {
            AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getString(getTitleId()))
                .setMessage(getString(R.string.omnipod_pod_activation_wizard_attach_pod_confirm_insert_cannula_text))
                .setPositiveButton(getString(R.string.omnipod_ok)) { _, _ -> findNavController().navigate(getNextPageActionId()) }
                .setNegativeButton(getString(R.string.omnipod_cancel), null)
                .show()
        }
    }
}