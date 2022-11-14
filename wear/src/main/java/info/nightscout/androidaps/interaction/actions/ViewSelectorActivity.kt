@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.interaction.actions

import android.content.Context
import android.os.Bundle
import android.support.wearable.view.GridViewPager
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.wear.widget.CurvedTextView
import dagger.android.DaggerActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.nondeprecated.DotsPageIndicatorNonDeprecated
import info.nightscout.androidaps.nondeprecated.GridPagerAdapterNonDeprecated
import info.nightscout.androidaps.nondeprecated.GridViewPagerNonDeprecated
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

/**
 * Created by adrian on 13/02/17.
 */
open class ViewSelectorActivity : DaggerActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus

    private var pager: GridViewPagerNonDeprecated? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.grid_layout)
        setTitleBasedOnScreenShape()
        pager = findViewById(R.id.pager)
        val dotsPageIndicator: DotsPageIndicatorNonDeprecated = findViewById(R.id.page_indicator)
        dotsPageIndicator.setPager(pager)
        pager?.setOnPageChangeListener(object : GridViewPager.OnPageChangeListener {
            override fun onPageScrolled(row: Int, column: Int, rowOffset: Float, columnOffset: Float, rowOffsetPixels: Int, columnOffsetPixels: Int) {
                dotsPageIndicator.onPageScrolled(row, column, rowOffset, columnOffset, rowOffsetPixels, columnOffsetPixels)
            }

            override fun onPageSelected(row: Int, column: Int) {
                dotsPageIndicator.onPageSelected(row, column)
                pager?.getChildAt(column)?.requestFocus()
            }

            override fun onPageScrollStateChanged(state: Int) {
                dotsPageIndicator.onPageScrollStateChanged(state)
            }
        })
    }

    fun setAdapter(adapter: GridPagerAdapterNonDeprecated?) {
        pager?.adapter = adapter
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
        Toast.makeText(context, getString(text), Toast.LENGTH_LONG).show()
    }
}
