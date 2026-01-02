package app.aaps.pump.equil.ui.pair

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.navigation.fragment.findNavController
import app.aaps.pump.equil.R
import com.bumptech.glide.Glide

class EquilPairAttachFragment : EquilPairFragmentBase() {

    private var imageView: ImageView? = null
    private var buttonNext: Button? = null

    override fun getLayoutId(): Int = R.layout.equil_pair_attach_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        imageView = view.findViewById(R.id.imv)
        buttonNext = view.findViewById(R.id.button_next)
        Glide.with(view)
            .asGif()
            .load(R.drawable.equil_animation_wizard_attach)
            .into(imageView!!)
        buttonNext?.setOnClickListener {
            context?.let {
                val nextPage = getNextPageActionId()
                findNavController().navigate(nextPage)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        buttonNext?.setOnClickListener(null)
        imageView?.let { Glide.with(this).clear(it) }
        imageView = null
        buttonNext = null
    }

    override fun getNextPageActionId(): Int = R.id.action_startEquilActivationFragment_to_startEquilPairAirFragment

    override fun getIndex(): Int = 4
}
