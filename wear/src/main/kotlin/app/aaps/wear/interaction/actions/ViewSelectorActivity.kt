package app.aaps.wear.interaction.actions

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.CurvedTextView
import androidx.wear.widget.WearableRecyclerView
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.R
import app.aaps.wear.widgets.PagerAdapter
import app.aaps.wear.widgets.SimplePageIndicator
import dagger.android.DaggerActivity
import javax.inject.Inject

/**
 * Base activity for horizontal paging actions using WearableRecyclerView.
 * Replaces deprecated GridViewPager with modern AndroidX components.
 */
open class ViewSelectorActivity : DaggerActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus

    private var pager: WearableRecyclerView? = null
    private var pageIndicator: SimplePageIndicator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pager)
        setTitleBasedOnScreenShape()

        pager = findViewById(R.id.pager)
        pageIndicator = findViewById(R.id.page_indicator)

        // Setup horizontal paging with snap behavior
        pager?.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            isEdgeItemsCenteringEnabled = false
            isCircularScrollingGestureEnabled = false
        }

        // Attach snap helper for page-snapping behavior
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(pager)

        // Listen for page changes to update indicator
        pager?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updatePageIndicator()
                }
            }
        })
    }

    fun setAdapter(adapter: PagerAdapter?) {
        pager?.adapter = adapter
        adapter?.let {
            pageIndicator?.setPageCount(it.getPageCount())
            updatePageIndicator()
        }
    }

    private fun updatePageIndicator() {
        val layoutManager = pager?.layoutManager as? LinearLayoutManager
        val currentPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
        if (currentPosition >= 0) {
            pageIndicator?.setCurrentPage(currentPosition)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pager?.adapter = null
    }

    private fun setTitleBasedOnScreenShape() {
        // intents can inject dynamic titles, otherwise we'll use the default
        var title: String? = this.title.toString()
        title = intent?.extras?.getString("title", title)
        val titleViewCurved: CurvedTextView = findViewById(R.id.title_curved)
        val titleView = findViewById<TextView>(R.id.title)
        if (this.resources.configuration.isScreenRound) {
            titleViewCurved.text = title
            titleViewCurved.visibility = View.VISIBLE
            titleView.visibility = View.GONE
        } else {
            titleView.text = title
            titleView.visibility = View.VISIBLE
            titleViewCurved.visibility = View.GONE
        }
    }

    fun showToast(context: Context?, text: Int) {
        Toast.makeText(context, getString(text), Toast.LENGTH_SHORT).show()
    }
}
