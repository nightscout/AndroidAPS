package app.aaps.plugins.main.general.nfcCommands

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
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

        adapter =
            NfcTagsAdapter(
                tagsList,
                onRename = { tag -> showRenameDialog(tag) },
                onDelete = { tag -> confirmDelete(tag) },
            )
        binding.tagsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.tagsRecycler.adapter = adapter
        binding.buildButton.setOnClickListener {
            startActivity(Intent(requireContext(), NfcBuildActivity::class.java))
        }

        refresh()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        refresh()
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

    private fun showRenameDialog(tag: NfcCreatedTag) {
        val input =
            EditText(requireContext()).apply {
                setText(tag.name)
                setSelection(tag.name.length)
                hint = getString(R.string.nfccommands_rename_tag)
                maxLines = 1
            }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.nfccommands_rename_tag_title)
            .setView(input)
            .setPositiveButton(R.string.nfccommands_rename_ok) { _, _ ->
                val updatedName = input.text?.toString()?.trim().orEmpty()
                if (updatedName.isBlank() || updatedName == tag.name) return@setPositiveButton
                NfcTokenSupport.saveCreatedTag(
                    requireContext(),
                    tag.copy(name = updatedName),
                )
                refresh()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
