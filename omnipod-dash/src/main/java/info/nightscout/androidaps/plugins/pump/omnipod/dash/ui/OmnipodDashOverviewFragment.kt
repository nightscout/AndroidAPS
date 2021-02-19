package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment

class OmnipodDashOverviewFragment : DaggerFragment() {

    private var _binding: OmnipodOverviewBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OmnipodOverviewBinding.inflate(inflater, container, false).also { _binding = it }.root
}