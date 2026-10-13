package app.aaps.pump.equil.ui.pair

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import app.aaps.pump.equil.R
import com.bumptech.glide.Glide

class EquilPairAssembleFragment : EquilPairFragmentBase() {

    private var imageView: ImageView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        imageView = view.findViewById(R.id.imv)
        Glide.with(view)
            .asGif()
            .load(R.drawable.equil_animation_wizard_assemble)
            .into(imageView!!)
        if ((activity as? EquilPairActivity)?.pair == false) {
            view.findViewById<TextView>(R.id.tv_hint1).text = rh.gs(R.string.equil_title_dressing)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        imageView?.let { Glide.with(this).clear(it) }
        imageView = null
    }

    override fun getLayoutId(): Int = R.layout.equil_pair_assemble_fragment

    override fun getNextPageActionId(): Int =
        if ((activity as? EquilPairActivity)?.pair == false) R.id.action_startEquilActivationFragment_to_startEquilPairFillFragment
        else R.id.action_startEquilActivationFragment_to_startEquilSerialNumberFragment

    override fun getIndex(): Int =
        if ((activity as? EquilPairActivity)?.pair == false) 2 else 1
}
