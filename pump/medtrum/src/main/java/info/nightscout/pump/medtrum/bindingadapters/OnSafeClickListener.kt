package info.nightscout.pump.medtrum.bindingadapters

import android.view.View
import java.util.concurrent.atomic.AtomicBoolean

class OnSafeClickListener(
        private val clickListener: View.OnClickListener,
        private val intervalMs: Long = MIN_CLICK_INTERVAL
) : View.OnClickListener {
    private var canClick = AtomicBoolean(true)

    override fun onClick(v: View?) {
        if (canClick.getAndSet(false)) {
            v?.run {
                postDelayed({
                    canClick.set(true)
                }, intervalMs)
                clickListener.onClick(v)
            }
        }
    }
    companion object {
        // Set duplicate click prevention time
        private const val MIN_CLICK_INTERVAL: Long = 1000
    }
}
