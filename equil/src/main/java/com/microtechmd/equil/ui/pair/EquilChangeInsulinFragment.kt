package com.microtechmd.equil.ui.pair

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.microtechmd.equil.R
import com.microtechmd.equil.data.RunMode
import com.microtechmd.equil.data.database.EquilHistoryRecord
import com.microtechmd.equil.data.database.ResolvedResult
import com.microtechmd.equil.driver.definition.ActivationProgress
import com.microtechmd.equil.manager.command.CmdInsulinChange
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.extensions.runOnUiThread
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.queue.Callback

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilChangeInsulinFragment : EquilPairFragmentBase() {

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_change_insulin_fragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Glide.with(this)
            .asGif()
            .load(R.drawable.equil_animation_wizard_detach)
            .into(view.findViewById<ImageView>(R.id.imv))

        view.findViewById<Button>(R.id.button_next).setOnClickListener {
            context?.let {
                showLoading();
                commandQueue.customCommand(CmdInsulinChange(), object : Callback() {
                    override fun run() {
                        dismissLoading();
                        if (result.success) {
                            equilPumpPlugin.resetData();
                            equilPumpPlugin.equilManager.runMode = RunMode.STOP;
                            // equilPumpPlugin.equilManager.activationProgress = ActivationProgress.CANNULA_CHANGE
                            runOnUiThread {
                                val nextPage = getNextPageActionId()
                                if (nextPage != null) {
                                    findNavController().navigate(nextPage)
                                }
                            }
                        } else {
                            dismissLoading();
                            equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                        }
                    }
                })
            }
        }
    }

    override fun getNextPageActionId(): Int? {
        return R.id.action_startEquilChangeInsulinFragment_to_startEquilPairAssembleFragment;
    }

    override fun getIndex(): Int {
        return 1
    }

}
