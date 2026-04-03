package app.aaps.plugins.main.general.nfcCommands

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.NfccommandsLogItemBinding
import java.text.DateFormat

class NfcLogAdapter(
    private val entries: MutableList<NfcLogEntry>,
) : RecyclerView.Adapter<NfcLogAdapter.ViewHolder>() {

    inner class ViewHolder(
        val binding: NfccommandsLogItemBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = NfccommandsLogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val entry = entries[position]
        val ctx = holder.itemView.context

        val actionLabel =
            if (entry.action == "WRITE") {
                ctx.getString(R.string.nfccommands_log_action_write)
            } else {
                ctx.getString(R.string.nfccommands_log_action_read)
            }
        holder.binding.logActionChip.text = actionLabel
        holder.binding.logTagName.text = entry.tagName
        holder.binding.logTimestamp.text = formatter.format(entry.timestamp)
        holder.binding.logMessage.text = entry.message
    }

    override fun getItemCount() = entries.size

    fun updateEntries(newEntries: List<NfcLogEntry>) {
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }

    companion object {
        private val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }
}
