package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info;

import android.os.Bundle
import android.view.View
import android.widget.TextView
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.FragmentBase

abstract class InfoFragmentBase : FragmentBase() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.omnipod_wizard_info_page_text)?.text = getText();
    }

    abstract fun getText(): String

    override fun getLayout(): Int {
        return R.layout.omnipod_replace_pod_wizard_info_page_fragment
    }

}