package com.microtechmd.equil.ui.pair

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.microtechmd.equil.R

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilPairAssembleFragment : EquilPairFragmentBase() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Glide.with(this)
            .asGif()
            .load(R.drawable.equil_animation_wizard_assemble)
            .into(view.findViewById<ImageView>(R.id.imv))
    }

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_assemble_fragment
    }

    override fun getNextPageActionId(): Int? {
        return R.id.action_startEquilActivationFragment_to_startEquilSerialNumberFragment
    }

    override fun getIndex(): Int {
        return 1;
    }

}
