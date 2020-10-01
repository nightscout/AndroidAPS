package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.info

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.ChangePodWizardFragmentBase
import kotlinx.android.synthetic.main.omnipod_change_pod_wizard_info_page_fragment.*

abstract class InfoFragmentBase : ChangePodWizardFragmentBase() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        omnipod_change_pod_wizard_info_page_text.setText(getTextId())
    }

    @StringRes
    abstract fun getTextId(): Int

    @LayoutRes
    override fun getLayoutId(): Int {
        return R.layout.omnipod_change_pod_wizard_info_page_fragment
    }

}