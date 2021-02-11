package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.common.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R

abstract class InfoFragmentBase : WizardFragmentBase() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.omnipod_wizard_info_page_text).setText(getTextId())
    }

    @StringRes
    abstract fun getTextId(): Int

    @LayoutRes
    override fun getLayoutId(): Int {
        return R.layout.omnipod_wizard_info_page_fragment
    }
}