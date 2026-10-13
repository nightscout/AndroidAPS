package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
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
import app.aaps.wear.widgets.PagerAdapter
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
        setAdapter(MyPagerAdapter())
        val vibrator = getSystemService(Vibrator::class.java)
        val vibratePattern = longArrayOf(0, 100, 50, 100, 50)
        val effect = VibrationEffect.createWaveform(vibratePattern, -1)
        vibrator?.vibrate(effect)
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyPagerAdapter : PagerAdapter() {

        override fun getPageCount(): Int = 2

        override fun instantiateItem(container: ViewGroup, position: Int): View = when (position) {
            0    -> {
                // Page 0: Message page
                LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_text, container, false).apply {
                    val textView = findViewById<TextView>(R.id.message)
                    val scrollView = findViewById<View>(R.id.message_scroll)
                    textView.text = message
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
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }

            else -> {
                // Page 1: Confirm page
                LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false).apply {
                    val confirmButton = findViewById<ImageView>(R.id.confirmbutton)
                    confirmButton.setOnClickListener { view ->
                        // Visual feedback: scale animation
                        view.animate()
                            .scaleX(1.2f)
                            .scaleY(1.2f)
                            .setDuration(150)
                            .start()

                        // Haptic feedback
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)

                        if (actionKey.isNotEmpty()) startService(IntentWearToMobile(this@AcceptActivity, actionKey))
                        startForegroundService(IntentCancelNotification(this@AcceptActivity))
                        finishAffinity()
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        }
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
