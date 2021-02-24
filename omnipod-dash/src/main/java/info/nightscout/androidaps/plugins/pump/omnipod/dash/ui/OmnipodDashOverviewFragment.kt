package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.databinding.OmnipodCommonOverviewButtonsBinding
import info.nightscout.androidaps.plugins.pump.omnipod.common.databinding.OmnipodCommonOverviewPodInfoBinding
import info.nightscout.androidaps.plugins.pump.omnipod.dash.databinding.OmnipodDashOverviewBinding
import info.nightscout.androidaps.plugins.pump.omnipod.dash.databinding.OmnipodDashOverviewBluetoothStatusBinding

class OmnipodDashOverviewFragment : DaggerFragment() {

    var _binding: OmnipodDashOverviewBinding? = null
    var _bluetoothStatusBinding: OmnipodDashOverviewBluetoothStatusBinding? = null
    var _podInfoBinding: OmnipodCommonOverviewPodInfoBinding? = null
    var _buttonBinding: OmnipodCommonOverviewButtonsBinding? = null

    // These properties are only valid between onCreateView and
    // onDestroyView.
    val binding get() = _binding!!
    val bluetoothStatusBinding get() = _bluetoothStatusBinding!!
    val podInfoBinding get() = _podInfoBinding!!
    val buttonBinding get() = _buttonBinding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OmnipodDashOverviewBinding.inflate(inflater, container, false).also {
            _buttonBinding = OmnipodCommonOverviewButtonsBinding.bind(it.root)
            _podInfoBinding = OmnipodCommonOverviewPodInfoBinding.bind(it.root)
            _bluetoothStatusBinding = OmnipodDashOverviewBluetoothStatusBinding.bind(it.root)
            _binding = it
        }.root
}