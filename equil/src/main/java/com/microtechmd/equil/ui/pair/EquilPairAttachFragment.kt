package com.microtechmd.equil.ui.pair

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.microtechmd.equil.R
import com.microtechmd.equil.data.database.EquilHistoryRecord
import com.microtechmd.equil.data.database.ResolvedResult
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.extensions.runOnUiThread
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilPairAttachFragment : EquilPairFragmentBase() {

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_attach_fragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Glide.with(this)
            .asGif()
            .load(R.drawable.equil_animation_wizard_attach)
            .into(view.findViewById<ImageView>(R.id.imv))
        view.findViewById<Button>(R.id.button_next).setOnClickListener {
            context?.let {
                val nextPage = getNextPageActionId()
                if (nextPage != null) {
                    findNavController().navigate(nextPage)
                }
            }
        }
    }

    override fun getNextPageActionId(): Int? {
        return R.id.action_startEquilActivationFragment_to_startEquilPairAirFragment
    }

    override fun getIndex(): Int {
        return 4
    }

}
