package info.nightscout.androidaps.plugins.general.wear

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.databinding.WearFragmentBinding
import javax.inject.Inject

class WearFragment : DaggerFragment() {

    @Inject lateinit var wearPlugin: WearPlugin

    private var _binding: WearFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = WearFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.resend.setOnClickListener { wearPlugin.resendDataToWatch() }
        binding.opensettings.setOnClickListener { wearPlugin.openSettings() }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}