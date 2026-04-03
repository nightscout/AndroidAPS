package app.aaps.plugins.main.general.nfcCommands

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.NfccommandsFragmentBinding
import app.aaps.plugins.main.databinding.NfccommandsWriteDialogBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import dagger.android.support.DaggerFragment

class NfcCommandsFragment : DaggerFragment() {
    @javax.inject.Inject lateinit var plugin: NfcCommandsPlugin

    private var bindingOrNull: NfccommandsFragmentBinding? = null
    private val binding get() = bindingOrNull!!

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pagerAdapter: NfcPagerAdapter

    @Volatile private var isWritingMode = false
    private var pendingCommands = emptyList<String>()
    private var pendingTagName = ""
    private var writeDialog: AlertDialog? = null
    private var pulseAnimSet: AnimatorSet? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var readerModeDisableRunnable: Runnable? = null

    companion object {
        // After a successful write the tag is still physically in range. Keeping reader
        // mode active for this window suppresses ACTION_NDEF_DISCOVERED (which would
        // otherwise execute the freshly-written commands immediately). By the time this
        // expires the user will have moved the phone away.
        private const val POST_WRITE_READER_MODE_HOLD_MS = 3_000L
    }

    /** Returns the active pump's TBR duration step in minutes (e.g. 30 or 60). */
    fun pumpBasalDurationStep(): Int = plugin.pumpBasalDurationStep()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingOrNull = NfccommandsFragmentBinding.inflate(inflater, container, false)
        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        pagerAdapter = NfcPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text =
                when (position) {
                    0 -> getString(R.string.nfccommands_tab_build)
                    else -> getString(R.string.nfccommands_tab_my_tags)
                }
        }.attach()

        return binding.root
    }

    fun startWriteMode(
        commands: List<String>,
        tagName: String = "",
    ) {
        if (commands.isEmpty()) return
        val adapter = nfcAdapter
        if (adapter == null) {
            Toast.makeText(requireContext(), getString(R.string.nfccommands_nfc_not_supported), Toast.LENGTH_SHORT).show()
            return
        }
        pendingCommands = commands
        pendingTagName = tagName
        isWritingMode = true

        showWriteDialog(commands)

        adapter.enableReaderMode(
            requireActivity(),
            { tag -> onTagDiscovered(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
            null,
        )
    }

    private fun showWriteDialog(commands: List<String>) {
        val dialogView = NfccommandsWriteDialogBinding.inflate(layoutInflater)

        // Set up circular ring backgrounds using primary color
        val primaryColor =
            TypedValue()
                .also {
                    requireContext().theme.resolveAttribute(android.R.attr.colorPrimary, it, true)
                }.data

        dialogView.ring1.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(primaryColor)
            }
        dialogView.ring2.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(3), primaryColor)
            }
        dialogView.ring3.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(2), primaryColor)
            }

        dialogView.writeSummary.text =
            commands
                .mapIndexed { i, cmd ->
                    getString(R.string.nfccommands_cascade_step_label, i + 1, cmd)
                }.joinToString("\n")

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView.root)
                .setCancelable(false)
                .create()

        dialogView.cancelButton.setOnClickListener {
            disableWritingMode()
        }

        dialog.show()
        writeDialog = dialog

        // Pulse animation
        startPulseAnimation(dialogView)
    }

    private fun startPulseAnimation(dialogView: NfccommandsWriteDialogBinding) {
        fun pulseAnim(
            view: View,
            startDelay: Long,
        ): AnimatorSet {
            val scaleX =
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.3f).apply {
                    duration = 900
                    repeatMode = ValueAnimator.RESTART
                    repeatCount = ValueAnimator.INFINITE
                    this.startDelay = startDelay
                }
            val scaleY =
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.3f).apply {
                    duration = 900
                    repeatMode = ValueAnimator.RESTART
                    repeatCount = ValueAnimator.INFINITE
                    this.startDelay = startDelay
                }
            val alpha =
                ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f).apply {
                    duration = 900
                    repeatMode = ValueAnimator.RESTART
                    repeatCount = ValueAnimator.INFINITE
                    this.startDelay = startDelay
                }
            return AnimatorSet().also { it.playTogether(scaleX, scaleY, alpha) }
        }

        val animSet = AnimatorSet()
        animSet.playTogether(
            pulseAnim(dialogView.ring1, 0),
            pulseAnim(dialogView.ring2, 200),
            pulseAnim(dialogView.ring3, 400),
        )
        animSet.start()
        pulseAnimSet = animSet
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // Called on the NFC binder thread — do not move to UI thread before the I/O is done.
    fun onTagDiscovered(tag: Tag) {
        if (!isWritingMode) return
        writeTag(tag)
    }

    // Runs on the NFC binder thread so that blocking I/O does not stall the main thread.
    // UI updates are posted back via requireActivity().runOnUiThread.
    private fun writeTag(tag: Tag) {
        val commands = pendingCommands
        val tagName = pendingTagName
        if (commands.isEmpty()) {
            requireActivity().runOnUiThread { disableWritingMode() }
            return
        }
        val name = tagName.ifBlank { commands.first() }
        val tagUid = NfcTokenSupport.tagUidHex(tag.id)
        val issued = NfcTokenSupport.issueToken(requireContext(), commands, tagUid = tagUid)
        val record = NdefRecord.createMime(NfcTokenSupport.MIME_TYPE, issued.token.toByteArray())
        val message = NdefMessage(arrayOf(record))

        val ndefFormatable = NdefFormatable.get(tag)
        val ndef = Ndef.get(tag)
        val success =
            when {
                ndefFormatable != null -> writeNdefFormatable(ndefFormatable, message)
                ndef != null -> writeNdef(ndef, message)
                else -> {
                    Log.e(LTag.NFC.tag, "Tag supports neither Ndef nor NdefFormatable. Supported techs: ${tag.techList.joinToString()}")
                    false
                }
            }

        if (success) {
            NfcTokenSupport.saveCreatedTag(
                requireContext(),
                NfcCreatedTag(
                    id = issued.tokenId,
                    name = name,
                    commands = commands,
                    token = issued.token,
                    createdAtMillis = issued.issuedAtMillis,
                    expiresAtMillis = issued.expiresAtMillis,
                ),
            )
            requireActivity().runOnUiThread {
                pagerAdapter.tagsFragment.refresh()
                pagerAdapter.buildFragment.clearChain()
                Toast.makeText(requireContext(), getString(R.string.nfccommands_tag_written), Toast.LENGTH_SHORT).show()
                // Delay disableReaderMode so that the tag still in the field cannot
                // trigger ACTION_NDEF_DISCOVERED immediately after writing.
                disableWritingMode(delayReaderModeDisable = true)
            }
        } else {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), getString(R.string.nfccommands_tag_write_error), Toast.LENGTH_SHORT).show()
                disableWritingMode()
            }
        }
    }

    private fun writeNdef(
        ndef: Ndef,
        message: NdefMessage,
    ): Boolean =
        try {
            ndef.connect()
            try {
                ndef.writeNdefMessage(message)
                true
            } finally {
                ndef.close()
            }
        } catch (e: Exception) {
            Log.e(LTag.NFC.tag, "Failed to write NDEF tag", e)
            false
        }

    private fun writeNdefFormatable(
        formatable: NdefFormatable,
        message: NdefMessage,
    ): Boolean =
        try {
            formatable.connect()
            try {
                formatable.format(message)
                true
            } finally {
                formatable.close()
            }
        } catch (e: Exception) {
            Log.e(LTag.NFC.tag, "Failed to format and write NDEF tag", e)
            false
        }

    // Disables write mode. When delayReaderModeDisable=true the NFC reader mode stays
    // active for POST_WRITE_READER_MODE_HOLD_MS after a successful write; reader mode
    // suppresses ACTION_NDEF_DISCOVERED so the freshly-written tag cannot auto-execute
    // while it is still in the field. The normal (immediate) path is used for cancels,
    // errors and fragment lifecycle stops.
    private fun disableWritingMode(delayReaderModeDisable: Boolean = false) {
        isWritingMode = false
        pulseAnimSet?.cancel()
        pulseAnimSet = null
        writeDialog?.dismiss()
        writeDialog = null
        if (delayReaderModeDisable) {
            scheduleReaderModeDisable()
        } else {
            cancelScheduledReaderModeDisable()
            nfcAdapter?.disableReaderMode(requireActivity())
        }
    }

    private fun scheduleReaderModeDisable() {
        cancelScheduledReaderModeDisable()
        val adapter = nfcAdapter ?: return
        val runnable =
            Runnable {
                if (isAdded) adapter.disableReaderMode(requireActivity())
            }
        readerModeDisableRunnable = runnable
        mainHandler.postDelayed(runnable, POST_WRITE_READER_MODE_HOLD_MS)
    }

    private fun cancelScheduledReaderModeDisable() {
        readerModeDisableRunnable?.let { mainHandler.removeCallbacks(it) }
        readerModeDisableRunnable = null
    }

    override fun onStop() {
        super.onStop()
        // Cancel any delayed disable and ensure reader mode is off immediately.
        // This covers both: active write mode and the post-write hold window.
        cancelScheduledReaderModeDisable()
        if (isWritingMode) {
            isWritingMode = false
            pulseAnimSet?.cancel()
            pulseAnimSet = null
            writeDialog?.dismiss()
            writeDialog = null
        }
        nfcAdapter?.disableReaderMode(requireActivity())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelScheduledReaderModeDisable()
        pulseAnimSet?.cancel()
        writeDialog?.dismiss()
        bindingOrNull = null
    }
}
