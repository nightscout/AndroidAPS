package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info;

import android.os.Bundle
import android.view.View
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.FragmentBase
import kotlinx.android.synthetic.main.omnipod_replace_pod_wizard_info_page_fragment.*

abstract class InfoFragmentBase : FragmentBase() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        omnipod_wizard_info_page_text.text = getText()
    }

    abstract fun getText(): String

    override fun getLayoutId(): Int {
        return R.layout.omnipod_replace_pod_wizard_info_page_fragment
    }

}