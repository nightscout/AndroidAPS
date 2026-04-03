package app.aaps.plugins.main.general.nfcCommands

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.ui.dragHelpers.ItemTouchHelperAdapter
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.NfccommandsCascadeStepItemBinding

class NfcChainAdapter(
    private val chain: MutableList<String>,
    private val onRemove: (Int) -> Unit,
) : RecyclerView.Adapter<NfcChainAdapter.ViewHolder>(),
    ItemTouchHelperAdapter {
    var touchHelper: ItemTouchHelper? = null

    inner class ViewHolder(
        val binding: NfccommandsCascadeStepItemBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = NfccommandsCascadeStepItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val ctx = holder.itemView.context
        holder.binding.stepLabel.text = ctx.getString(R.string.nfccommands_cascade_step_label, position + 1, chain[position])

        holder.binding.stepRemoveButton.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) onRemove(pos)
        }

        @Suppress("ClickableViewAccessibility")
        holder.binding.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                touchHelper?.startDrag(holder)
            }
            false
        }
    }

    override fun getItemCount() = chain.size

    override fun onItemMove(
        fromPosition: Int,
        toPosition: Int,
    ): Boolean {
        val item = chain.removeAt(fromPosition)
        chain.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onDrop() {
        notifyDataSetChanged()
    }
}
