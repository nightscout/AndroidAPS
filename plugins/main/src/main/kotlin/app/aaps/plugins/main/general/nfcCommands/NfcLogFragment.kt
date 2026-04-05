package app.aaps.plugins.main.general.nfcCommands

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import app.aaps.plugins.main.databinding.NfccommandsLogFragmentBinding

class NfcLogFragment : Fragment() {
    private var bindingOrNull: NfccommandsLogFragmentBinding? = null
    private val binding get() = bindingOrNull!!

    private val logEntries = mutableListOf<NfcLogEntry>()
    private lateinit var adapter: NfcLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingOrNull = NfccommandsLogFragmentBinding.inflate(inflater, container, false)

        adapter = NfcLogAdapter(logEntries)
        binding.logRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.logRecycler.adapter = adapter

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun refresh() {
        val binding = bindingOrNull ?: return
        val entries = NfcTokenSupport.loadLog(requireContext())
        adapter.updateEntries(entries)
        binding.logEmptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        binding.logRecycler.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingOrNull = null
    }
}
