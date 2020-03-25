package info.nightscout.androidaps.plugins.general.wear

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import kotlinx.android.synthetic.main.wear_fragment.*
import javax.inject.Inject

class WearFragment : DaggerFragment() {
    @Inject lateinit var wearPlugin: WearPlugin

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.wear_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wear_resend.setOnClickListener { wearPlugin.resendDataToWatch() }
        wear_opensettings.setOnClickListener { wearPlugin.openSettings() }
    }
}