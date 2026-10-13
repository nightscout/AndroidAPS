package app.aaps.wear.widgets

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * Base adapter for horizontal paging with WearableRecyclerView.
 * Similar API to GridPagerAdapter but for modern RecyclerView.
 */
abstract class PagerAdapter : RecyclerView.Adapter<PagerAdapter.PageViewHolder>() {

    /**
     * Return the number of pages.
     */
    abstract fun getPageCount(): Int

    /**
     * Create the view for the given page position.
     * @param container The parent ViewGroup
     * @param position The page position (0-indexed)
     * @return The view for this page
     */
    abstract fun instantiateItem(container: ViewGroup, position: Int): View

    // RecyclerView.Adapter implementation
    override fun getItemCount(): Int = getPageCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = instantiateItem(parent, viewType)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        // Views are created in onCreateViewHolder, binding is position-aware
        // No additional binding needed for simple pagers
    }

    override fun getItemViewType(position: Int): Int = position

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
