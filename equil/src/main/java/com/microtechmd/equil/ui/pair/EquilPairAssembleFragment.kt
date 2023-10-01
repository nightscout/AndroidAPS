package com.microtechmd.equil.ui.pair

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.microtechmd.equil.R
import com.microtechmd.equil.driver.definition.ActivationProgress

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilPairAssembleFragment : EquilPairFragmentBase() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Glide.with(this)
            .asGif()
            .load(R.drawable.equil_animation_wizard_assemble)
            .into(view.findViewById<ImageView>(R.id.imv))
        if ((activity as? EquilPairActivity)?.pair == false) {
            view.findViewById<TextView>(R.id.tv_hint1).text = rh.gs(R.string.equil_title_dressing)
        }
    }

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_assemble_fragment
    }

    override fun getNextPageActionId(): Int? {
        if ((activity as? EquilPairActivity)?.pair == false) {
            return R.id.action_startEquilActivationFragment_to_startEquilPairFillFragment

        }
        return R.id.action_startEquilActivationFragment_to_startEquilSerialNumberFragment
    }

    override fun getIndex(): Int {
        if ((activity as? EquilPairActivity)?.pair == false) {
            return 2
        }
        return 1
    }

}
