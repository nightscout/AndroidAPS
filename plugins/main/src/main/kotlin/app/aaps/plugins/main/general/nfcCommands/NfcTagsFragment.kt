package app.aaps.plugins.main.general.nfcCommands

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.NfccommandsTagsFragmentBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class NfcTagsFragment : Fragment() {
    private var _binding: NfccommandsTagsFragmentBinding? = null
    private val binding get() = _binding!!

    private val tagsList = mutableListOf<NfcCreatedTag>()
    private lateinit var adapter: NfcTagsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = NfccommandsTagsFragmentBinding.inflate(inflater, container, false)

        adapter = NfcTagsAdapter(tagsList) { tag -> confirmDelete(tag) }
        binding.tagsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.tagsRecycler.adapter = adapter

        refresh()

        return binding.root
    }

    fun refresh() {
        val binding = _binding ?: return
        val tags = NfcTokenSupport.loadCreatedTags(requireContext())
        adapter.updateTags(tags)
        binding.emptyState.visibility = if (tags.isEmpty()) View.VISIBLE else View.GONE
        binding.tagsRecycler.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmDelete(tag: NfcCreatedTag) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.nfccommands_delete_confirm_title)
            .setMessage(R.string.nfccommands_delete_confirm_msg)
            .setPositiveButton(R.string.nfccommands_delete_confirm_ok) { _, _ ->
                NfcTokenSupport.blacklistTag(requireContext(), tag)
                refresh()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
