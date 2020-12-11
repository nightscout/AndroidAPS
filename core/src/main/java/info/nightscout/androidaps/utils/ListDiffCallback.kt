package info.nightscout.androidaps.utils

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView

open class ListDiffCallback<T>(private val newItems: List<T>, private val oldItems: List<T>) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldItems.size

    override fun getNewListSize(): Int = newItems.size

    /**
     * Called by the DiffUtil to decide whether two object represent the same Item.
     * <p>
     * For example, if your items have unique ids, this method should check their id equality.
     *
     * @param oldItemPosition The position of the item in the old list
     * @param newItemPosition The position of the item in the new list
     * @return True if the two items represent the same object or false if they are different.
     */
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        newItems[newItemPosition] == oldItems[oldItemPosition]

    /**
     * Called by the DiffUtil when it wants to check whether two items have the same data.
     * DiffUtil uses this information to detect if the contents of an item has changed.
     * <p>
     * DiffUtil uses this method to check equality instead of {@link Object#equals(Object)}
     * so that you can change its behavior depending on your UI.
     * For example, if you are using DiffUtil with a
     * {@link RecyclerView.Adapter RecyclerView.Adapter}, you should
     * return whether the items' visual representations are the same.
     * <p>
     * This method is called only if {@link #areItemsTheSame(int, int)} returns
     * {@code true} for these items.
     *
     * @param oldItemPosition The position of the item in the old list
     * @param newItemPosition The position of the item in the new list which replaces the
     *                        oldItem
     * @return True if the contents of the items are the same or false if they are different.
     */
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        newItems[newItemPosition] == oldItems[oldItemPosition]
}

class ListUpdateCallbackHelper(val adapter: RecyclerView.Adapter<*>, val insertCallback: (Int) -> Unit) : ListUpdateCallback {

    override fun onChanged(position: Int, count: Int, payload: Any?) {
        adapter.notifyItemRangeChanged(position, count, payload)
    }

    override fun onInserted(position: Int, count: Int) {
        adapter.notifyItemRangeInserted(position, count)
        insertCallback(position)
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
        adapter.notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRemoved(position: Int, count: Int) {
        adapter.notifyItemRangeRemoved(position, count)
    }
}