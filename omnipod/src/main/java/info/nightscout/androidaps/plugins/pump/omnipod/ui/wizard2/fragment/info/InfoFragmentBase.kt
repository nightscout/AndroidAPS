package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info;

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.omnipod.R

abstract class InfoFragmentBase : DaggerFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.omnipod_replace_pod_wizard_info_page_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.omnipod_wizard_info_page_text)?.text = getText();
    }

    abstract fun getText(): String

}