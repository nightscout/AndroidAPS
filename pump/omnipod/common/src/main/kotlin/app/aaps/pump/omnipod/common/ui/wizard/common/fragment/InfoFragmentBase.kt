package app.aaps.pump.omnipod.common.ui.wizard.common.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.LayoutRes
import app.aaps.pump.omnipod.common.R

abstract class InfoFragmentBase : WizardFragmentBase() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.omnipod_wizard_info_page_text).setText(getTextId())
    }

    @LayoutRes
    override fun getLayoutId(): Int = R.layout.omnipod_common_wizard_info_page_fragment
}