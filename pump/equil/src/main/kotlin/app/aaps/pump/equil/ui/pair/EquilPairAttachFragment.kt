package app.aaps.pump.equil.ui.pair

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.navigation.fragment.findNavController
import app.aaps.pump.equil.R
import com.bumptech.glide.Glide

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilPairAttachFragment : EquilPairFragmentBase() {

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_attach_fragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Glide.with(view)
            .asGif()
            .load(R.drawable.equil_animation_wizard_attach)
            .into(view.findViewById(R.id.imv))
        view.findViewById<Button>(R.id.button_next).setOnClickListener {
            context?.let {
                val nextPage = getNextPageActionId()
                findNavController().navigate(nextPage)
            }
        }
    }

    override fun getNextPageActionId(): Int {
        return R.id.action_startEquilActivationFragment_to_startEquilPairAirFragment
    }

    override fun getIndex(): Int {
        return 4
    }

}
