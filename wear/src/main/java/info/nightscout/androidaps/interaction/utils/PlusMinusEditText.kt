package info.nightscout.androidaps.interaction.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.*
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.OnGenericMotionListener
import android.view.View.OnTouchListener
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.Pair

/**
 * Created by mike on 28.06.2016.
 */
@SuppressLint("SetTextI18n") class PlusMinusEditText @JvmOverloads constructor(
    view: View,
    editTextID: Int,
    private var plusButtons: List<Pair<Int, Double>>,
    minusID: Int,
    initValue: Double,
    private val minValue: Double,
    private val maxValue: Double,
    private val stepGeneral: Double,
    private val formatter: NumberFormat,
    private val allowZero: Boolean,
    private val roundRobin: Boolean = false,
) : View.OnKeyListener, OnTouchListener, View.OnClickListener, OnGenericMotionListener {

    constructor(
        view: View,
        editTextID: Int,
        plusID: Int,
        minusID: Int,
        initValue: Double,
        minValue: Double,
        maxValue: Double,
        stepGeneral: Double,
        formatter: NumberFormat,
        allowZero: Boolean,
        roundRobin: Boolean = false
    ) : this(view, editTextID, listOf(Pair(plusID, stepGeneral)), minusID, initValue, minValue, maxValue, stepGeneral, formatter, allowZero, roundRobin)

    var editText: TextView
        private set
    private var minusImage: View
    private var plusImage1: View
    private var plusImage2: AppCompatButton? = null
    private var plusImage3: AppCompatButton? = null
    private var value: Double
    private val context: Context
    private var mChangeCounter = 0
    private var mLastChange: Long = 0
    private val mHandler: Handler
    private var mUpdater: ScheduledExecutorService? = null

    private inner class UpdateCounterTask(private val mInc: Boolean, private val step: Double) : Runnable {

        private var repeated = 0
        private var multiplier = 1
        override fun run() {
            val msg = Message()
            val doubleLimit = 5
            val multipleButtons = mInc && (plusImage2 != null || plusImage3 != null)
            if (!multipleButtons && repeated % doubleLimit == 0) multiplier *= 2
            val bundle = Bundle()
            bundle.putDouble("step", step)
            bundle.putInt("multiplier", multiplier)
            msg.data = bundle

            if (mInc) {
                msg.what = MSG_INC
            } else {
                msg.what = MSG_DEC
            }
            mHandler.sendMessage(msg)
        }
    }

    private fun inc(multiplier: Int, step: Double, vibrate: Boolean = true) {
        value += step * multiplier
        if (value > maxValue) {
            if (roundRobin) {
                value = minValue
            } else {
                value = maxValue
                stopUpdating()
            }
        }
        updateEditText()
        if (vibrate) vibrateDevice()
    }

    private fun dec(multiplier: Int, step: Double, vibrate: Boolean = true) {
        value -= step * multiplier
        if (value < minValue) {
            if (roundRobin) {
                value = maxValue
            } else {
                value = minValue
                stopUpdating()
            }
        }
        updateEditText()
        if (vibrate) vibrateDevice()
    }

    fun vibrateDevice() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    private fun updateEditText() {
        if (value == 0.0 && !allowZero) editText.text = "" else editText.text = formatter.format(value)
    }

    private fun startUpdating(inc: Boolean, step: Double) {
        if (mUpdater != null) {
            return
        }

        mUpdater = Executors.newSingleThreadScheduledExecutor()
        mUpdater?.scheduleAtFixedRate(
            UpdateCounterTask(inc, step), 200, 200,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopUpdating() {
        mUpdater?.shutdownNow()
        mUpdater = null
    }

    private fun getStep(v: View): Double {
        return when (v) {
            plusImage1 -> plusButtons[0].second
            plusImage2 -> plusButtons[1].second
            plusImage3 -> plusButtons[2].second
            else       -> stepGeneral
        }
    }

    private fun isIncrement(v: View): Boolean {
        return when (v) {
            plusImage1 -> true
            plusImage2 -> true
            plusImage3 -> true
            else       -> false
        }
    }

    override fun onClick(v: View) {
        if (mUpdater == null) {
            if (isIncrement(v)) {
                inc(1, getStep(v))
            } else {
                dec(1, getStep(v))
            }
        }
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        val isKeyOfInterest = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
        val isReleased = event.action == KeyEvent.ACTION_UP
        val isPressed = event.action == KeyEvent.ACTION_DOWN
        if (isKeyOfInterest && isReleased) {
            stopUpdating()
        } else if (isKeyOfInterest && isPressed) {
            startUpdating(isIncrement(v), stepGeneral)
        }
        return false
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val isReleased = event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL
        val isPressed = event.action == MotionEvent.ACTION_DOWN
        if (isReleased) {
            stopUpdating()
        } else if (isPressed) {
            startUpdating(isIncrement(v), getStep(v))
        }
        return false
    }

    override fun onGenericMotion(v: View, ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            val now = System.currentTimeMillis()
            if (now - mLastChange > THRESHOLD_TIME) mChangeCounter = 0
            val dynamicMultiplier = if (mChangeCounter < THRESHOLD_COUNTER) 1 else if (mChangeCounter < THRESHOLD_COUNTER_LONG) 2 else 4
            val delta = -ev.getAxisValue(MotionEventCompat.AXIS_SCROLL)
            if (delta > 0) {
                inc(dynamicMultiplier, stepGeneral, false)
            } else {
                dec(dynamicMultiplier, stepGeneral, false)
            }
            mLastChange = System.currentTimeMillis()
            mChangeCounter++
            return true
        }
        return false
    }

    companion object {

        private const val THRESHOLD_COUNTER = 5
        private const val THRESHOLD_COUNTER_LONG = 10
        private const val THRESHOLD_TIME = 100
        private const val MSG_INC = 0
        private const val MSG_DEC = 1
    }

    init {
        context = view.context
        editText = view.findViewById(editTextID)
        minusImage = view.findViewById(minusID)
        plusImage1 = view.findViewById(plusButtons.first().first)
        val format = DecimalFormat("#.#")
        plusButtons.getOrNull(1)?.let {
            plusImage2 = view.findViewById(it.first)
            plusImage2?.text = "+${format.format(it.second).replaceFirst("^0+(?!$)".toRegex(), "")}"
            plusImage2?.visibility = View.VISIBLE
        }
        plusButtons.getOrNull(2)?.let {
            plusImage3 = view.findViewById(it.first)
            plusImage3?.text = "+${format.format(it.second).replaceFirst("^0+(?!$)".toRegex(), "")}"
            plusImage3?.visibility = View.VISIBLE
        }

        value = initValue
        mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                val multiplier = msg.data.getInt("multiplier")
                val step = msg.data.getDouble("step")

                when (msg.what) {
                    MSG_INC -> {
                        inc(multiplier, step)
                        return
                    }

                    MSG_DEC -> {
                        dec(multiplier, step)
                        return
                    }
                }
                super.handleMessage(msg)
            }
        }

        editText.showSoftInputOnFocus = false
        editText.setTextIsSelectable(false)
        minusImage.setOnTouchListener(this)
        minusImage.setOnKeyListener(this)
        minusImage.setOnClickListener(this)
        plusImage1.setOnTouchListener(this)
        plusImage1.setOnKeyListener(this)
        plusImage1.setOnClickListener(this)
        plusImage2?.setOnTouchListener(this)
        plusImage2?.setOnKeyListener(this)
        plusImage2?.setOnClickListener(this)
        plusImage3?.setOnTouchListener(this)
        plusImage3?.setOnKeyListener(this)
        plusImage3?.setOnClickListener(this)
        editText.setOnGenericMotionListener(this)
        updateEditText()
    }

}
