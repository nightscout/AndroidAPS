package info.nightscout.androidaps.plugins.general.smsCommunicator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.smsCommunicator.fragments.SmsCommunicatorLogFragment
import info.nightscout.androidaps.plugins.general.smsCommunicator.fragments.SmsCommunicatorOtpFragment
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.OneTimePassword
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.smscommunicator_fragment.*
import javax.inject.Inject

class SmsCommunicatorFragment : DaggerFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var otp: OneTimePassword
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.smscommunicator_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        smscomunicator_tab_log.setOnClickListener {
            setFragment(SmsCommunicatorLogFragment())
            setBackgroundColorOnSelected(it)
        }
        smscomunicator_tab_otp.setOnClickListener {
            setFragment(SmsCommunicatorOtpFragment())
            setBackgroundColorOnSelected(it)
        }

        setFragment(SmsCommunicatorLogFragment())
        setBackgroundColorOnSelected(smscomunicator_tab_log)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        updateGui()
    }

    fun updateGui() {
        if (otp.isEnabled()) {
            smscomunicator_tab_otp.visibility = View.VISIBLE
        } else {
            if (smscomunicator_tab_otp.visibility != View.GONE) {
                setFragment(SmsCommunicatorLogFragment())
                setBackgroundColorOnSelected(smscomunicator_tab_log)
                smscomunicator_tab_otp.visibility = View.GONE
            }
        }
    }

    private fun setFragment(selectedFragment: Fragment) {
        val ft = childFragmentManager.beginTransaction()
        ft.replace(R.id.smscomunicator_fragment_container, selectedFragment)
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        ft.addToBackStack(null)
        ft.commit()
    }

    private fun setBackgroundColorOnSelected(selected: View) {
        smscomunicator_tab_log.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        smscomunicator_tab_otp.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        selected.setBackgroundColor(resourceHelper.gc(R.color.tabBgColorSelected))
    }

}