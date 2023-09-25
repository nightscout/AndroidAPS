package app.aaps.core.ui.dragHelpers

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.END
import androidx.recyclerview.widget.ItemTouchHelper.START
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView

const val ALPHA_FULL = 1f
const val ALPHA_DRAGGING = 0.5f

class SimpleItemTouchHelperCallback : ItemTouchHelper.SimpleCallback(UP or DOWN or START or END, 0) {

    override fun isLongPressDragEnabled() = false

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        val adapter = recyclerView.adapter as ItemTouchHelperAdapter
        val from = viewHolder.layoutPosition
        val to = target.layoutPosition
        adapter.onItemMove(from, to)

        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = ALPHA_DRAGGING
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = ALPHA_FULL
        (recyclerView.adapter as ItemTouchHelperAdapter).onDrop()
    }
}
