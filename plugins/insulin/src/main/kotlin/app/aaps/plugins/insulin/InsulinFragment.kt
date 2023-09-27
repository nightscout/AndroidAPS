package app.aaps.plugins.insulin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.insulin.databinding.InsulinFragmentBinding
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class InsulinFragment : DaggerFragment() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rh: ResourceHelper

    private var _binding: InsulinFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InsulinFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.name.text = activePlugin.activeInsulin.friendlyName
        binding.comment.text = activePlugin.activeInsulin.comment
        binding.dia.text = rh.gs(app.aaps.core.ui.R.string.dia) + ":  " + rh.gs(app.aaps.core.ui.R.string.format_hours, activePlugin.activeInsulin.dia)
        binding.graph.show(activePlugin.activeInsulin)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}