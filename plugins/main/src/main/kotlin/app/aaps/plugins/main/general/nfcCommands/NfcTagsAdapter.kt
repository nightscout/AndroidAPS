package app.aaps.plugins.main.general.nfcCommands

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.NfccommandsTagItemBinding
import java.text.DateFormat

class NfcTagsAdapter(
    private val tags: MutableList<NfcCreatedTag>,
    private val onDelete: (NfcCreatedTag) -> Unit,
) : RecyclerView.Adapter<NfcTagsAdapter.ViewHolder>() {
    inner class ViewHolder(
        val binding: NfccommandsTagItemBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = NfccommandsTagItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val tag = tags[position]
        val ctx = holder.itemView.context
        val now = System.currentTimeMillis()

        holder.binding.tagName.text = tag.name
        holder.binding.tagCommands.text =
            tag.commands
                .mapIndexed { i, cmd ->
                    ctx.getString(R.string.nfccommands_cascade_step_label, i + 1, cmd)
                }.joinToString("\n")

        val dayMillis = 24L * 60L * 60L * 1000L
        when {
            tag.isExpired(now) -> {
                holder.binding.tagExpiry.text = ctx.getString(R.string.nfccommands_tag_expired_at, formatter.format(tag.expiresAtMillis))
                holder.binding.tagExpiry.chipBackgroundColor =
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.nfccommands_tag_expired))
            }
            tag.isExpiringSoon(now) -> {
                val daysLeft = ((tag.expiresAtMillis - now) / dayMillis).toInt().coerceAtLeast(0)
                holder.binding.tagExpiry.text = ctx.getString(R.string.nfccommands_tag_expires_soon, daysLeft)
                holder.binding.tagExpiry.chipBackgroundColor =
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.nfccommands_tag_expiring_soon))
            }
            else -> {
                holder.binding.tagExpiry.text = ctx.getString(R.string.nfccommands_tag_expires_at, formatter.format(tag.expiresAtMillis))
                holder.binding.tagExpiry.chipBackgroundColor =
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.nfccommands_tag_valid))
            }
        }

        holder.binding.tagDeleteButton.setOnClickListener {
            onDelete(tag)
        }
    }

    override fun getItemCount() = tags.size

    companion object {
        private val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }

    fun updateTags(newTags: List<NfcCreatedTag>) {
        tags.clear()
        tags.addAll(newTags)
        notifyDataSetChanged()
    }
}
