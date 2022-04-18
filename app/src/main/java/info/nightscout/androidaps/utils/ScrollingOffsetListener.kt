package info.nightscout.androidaps.utils

import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout

class ScrollingOffsetListener(
    private val scrollView: ViewPager2
): AppBarLayout.OnOffsetChangedListener {

    private var originalHeight = 0
    private var firstOffset = true

    override fun onOffsetChanged(layout: AppBarLayout?, offset: Int) {
        if(firstOffset) {
            firstOffset = false
            originalHeight = scrollView.measuredHeight
        }

        val params = scrollView.layoutParams
        params.height = originalHeight + (offset * -1)

        scrollView.layoutParams = params
    }
}
