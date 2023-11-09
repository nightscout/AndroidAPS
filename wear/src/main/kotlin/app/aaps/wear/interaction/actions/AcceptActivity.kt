@file:Suppress("DEPRECATION")

package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.comm.IntentCancelNotification
import app.aaps.wear.comm.IntentWearToMobile
import app.aaps.wear.nondeprecated.GridPagerAdapterNonDeprecated
import kotlin.math.roundToInt

class AcceptActivity : ViewSelectorActivity() {

    var message = ""
    var actionKey = ""
    private var dismissThread: DismissThread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dismissThread = DismissThread()
        dismissThread?.start()
        val extras = intent.extras
        message = extras?.getString(DataLayerListenerServiceWear.KEY_MESSAGE, "") ?: ""
        actionKey = extras?.getString(DataLayerListenerServiceWear.KEY_ACTION_DATA, "") ?: ""
        if (message.isEmpty()) {
            finish()
            return
        }
        setAdapter(MyGridViewPagerAdapter())
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        val vibratePattern = longArrayOf(0, 100, 50, 100, 50)
        vibrator.vibrate(vibratePattern, -1)
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyGridViewPagerAdapter : GridPagerAdapterNonDeprecated() {

        override fun getColumnCount(arg0: Int): Int = 2
        override fun getRowCount(): Int = 1

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): View = when (col) {
            0    -> {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_text, container, false)
                val textView = view.findViewById<TextView>(R.id.message)
                val scrollView = view.findViewById<View>(R.id.message_scroll)
                textView.text = message
                container.addView(view)
                scrollView.setOnGenericMotionListener { v: View, ev: MotionEvent ->
                    if (ev.action == MotionEvent.ACTION_SCROLL &&
                        ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
                    ) {
                        val delta = -ev.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                            ViewConfigurationCompat.getScaledVerticalScrollFactor(
                                ViewConfiguration.get(container.context),
                                container.context
                            )
                        v.scrollBy(0, delta.roundToInt())
                        return@setOnGenericMotionListener true
                    }
                    false
                }
                scrollView.requestFocus()
                view
            }

            else -> {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    if (actionKey.isNotEmpty()) startService(IntentWearToMobile(this@AcceptActivity, actionKey))
                    startService(IntentCancelNotification(this@AcceptActivity))
                    finishAffinity()
                }
                container.addView(view)
                view
            }
        }

        override fun destroyItem(container: ViewGroup, row: Int, col: Int, view: Any) {
            // Handle this to get the data before the view is destroyed?
            // Object should still be kept by this, just setup for re-init?
            container.removeView(view as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`
    }

    @Synchronized public override fun onDestroy() {
        dismissThread?.invalidate()
        super.onDestroy()
    }

    private inner class DismissThread : Thread() {

        private var valid = true
        @Synchronized fun invalidate() {
            valid = false
        }

        override fun run() {
            SystemClock.sleep((60 * 1000L))
            synchronized(this) { if (valid) finish() }
        }
    }

    @Synchronized override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dismissThread?.invalidate()
        intent.extras?.let {
            startActivity(Intent(this, AcceptActivity::class.java).apply { putExtras(it) })
            finish()
        }
    }
}
