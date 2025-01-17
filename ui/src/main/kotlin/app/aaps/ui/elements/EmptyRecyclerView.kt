package app.aaps.ui.elements

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.extensions.toVisibility

class EmptyRecyclerView : RecyclerView {

    private var mEmptyView: View? = null
    private var mLoadingView: View? = null
    private var mIsLoading = true

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    private fun updateEmptyView() {
        runOnUiThread {
            val isEmpty = !mIsLoading && (adapter == null || adapter?.itemCount == 0)
            visibility = isEmpty.not().toVisibility()
            mEmptyView?.visibility = isEmpty.toVisibility()
        }
    }

    private fun updateLoadingView() {
        runOnUiThread {
            mLoadingView?.visibility = mIsLoading.toVisibility()
        }
    }

    private val observer: AdapterDataObserver = object : AdapterDataObserver() {
        override fun onChanged() {
            super.onChanged()
            updateEmptyView()
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            super.onItemRangeInserted(positionStart, itemCount)
            updateEmptyView()
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            super.onItemRangeRemoved(positionStart, itemCount)
            updateEmptyView()
        }
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        val oldAdapter = getAdapter()
        super.setAdapter(adapter)
        update(oldAdapter, adapter)
    }

    override fun swapAdapter(adapter: Adapter<*>?, removeAndRecycleExistingViews: Boolean) {
        val oldAdapter = getAdapter()
        super.swapAdapter(adapter, removeAndRecycleExistingViews)
        update(oldAdapter, adapter)
    }

    var emptyView: View?
        get() = mEmptyView
        set(view) {
            mEmptyView = view
            updateEmptyView()
        }

    var loadingView: View?
        get() = mLoadingView
        set(view) {
            mLoadingView = view
            updateLoadingView()
        }

    var isLoading: Boolean
        get() = mIsLoading
        set(loading) {
            mIsLoading = loading
            updateLoadingView()
        }

    private fun update(oldAdapter: Adapter<*>?, newAdapter: Adapter<*>?) {
        oldAdapter?.unregisterAdapterDataObserver(observer)
        newAdapter?.registerAdapterDataObserver(observer)
        updateEmptyView()
        isLoading = false
        updateLoadingView()
    }
}
