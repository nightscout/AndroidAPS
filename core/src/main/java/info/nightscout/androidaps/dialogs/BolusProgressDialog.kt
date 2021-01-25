package info.nightscout.androidaps.dialogs

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.ms_square.etsyblur.AlwaysAsyncPolicy
import com.ms_square.etsyblur.BlurConfig
import com.ms_square.etsyblur.BlurDialogFragment
import com.ms_square.etsyblur.SimpleAsyncPolicy
import com.ms_square.etsyblur.SmartAsyncPolicy
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.activities.BolusProgressHelperActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.core.databinding.DialogBolusprogressBinding
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

class BolusProgressDialog : BlurDialogFragment() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var sp: SP

    private val disposable = CompositeDisposable()

    companion object {

        @JvmField
        var bolusEnded = false

        @JvmField
        var stopPressed = false
    }

    private var running = true
    private var amount = 0.0
    private var state: String? = null
    private var helpActivity: BolusProgressHelperActivity? = null

    fun setInsulin(amount: Double): BolusProgressDialog {
        this.amount = amount
        bolusEnded = false
        return this
    }

    fun setHelperActivity(activity: BolusProgressHelperActivity): BolusProgressDialog {
        helpActivity = activity
        return this
    }

    private var _binding: DialogBolusprogressBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)

        val themeToSet = sp.getInt("theme", ThemeUtil.THEME_DARKSIDE)
        try {
            val theme: Resources.Theme? = context?.theme
            // https://stackoverflow.com/questions/11562051/change-activitys-theme-programmatically
            theme?.applyStyle(ThemeUtil.getThemeId(themeToSet), true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val drawable: Drawable? = context?.let { ContextCompat.getDrawable(it, R.drawable.dialog) }
        if ( sp.getBoolean("daynight", true)) {
            if (drawable != null) {
                drawable.setColorFilter(sp.getInt("darkBackgroundColor", R.color.background_dark), PorterDuff.Mode.SRC_IN)
            }
        } else {
            if (drawable != null) {
                drawable.setColorFilter(sp.getInt("lightBackgroundColor", R.color.background_light), PorterDuff.Mode.SRC_IN)
            }
        }
        dialog?.window?.setBackgroundDrawable(drawable)

        context?.let { SimpleAsyncPolicy () }?.let {
            BlurConfig.Builder()
                .overlayColor(ContextCompat.getColor(requireContext(), R.color.white_alpha_40))  // semi-transparent white color
                .debug(false)
                .asyncPolicy(it)
                .build()
        }
        _binding = DialogBolusprogressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            amount = it.getDouble("amount")
        }
        binding.title.text = resourceHelper.gs(R.string.goingtodeliver, amount)
        binding.stop.setOnClickListener {
            aapsLogger.debug(LTag.UI, "Stop bolus delivery button pressed")
            stopPressed = true
            binding.stoppressed.visibility = View.VISIBLE
            binding.stop.visibility = View.INVISIBLE
            commandQueue.cancelAllBoluses()
        }
        val defaultState = resourceHelper.gs(R.string.waitingforpump)
        binding.progressbar.max = 100
        state = savedInstanceState?.getString("state", defaultState) ?: defaultState
        binding.status.text = state
        stopPressed = false
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onResume() {
        super.onResume()
        aapsLogger.debug(LTag.UI, "onResume")
        if (!commandQueue.bolusInQueue())
            bolusEnded = true

        if (bolusEnded) dismiss()
        else running = true

        disposable.add(rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ binding.status.text = it.getStatus(resourceHelper) }) { fabricPrivacy.logException(it) }
        )
        disposable.add(rxBus
            .toObservable(EventDismissBolusProgressIfRunning::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ if (running) dismiss() }) { fabricPrivacy.logException(it) }
        )
        disposable.add(rxBus
            .toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                aapsLogger.debug(LTag.UI, "Status: ${it.status} Percent: ${it.percent}")
                binding.status.text = it.status
                binding.progressbar.progress = it.percent
                if (it.percent == 100) {
                    binding.stop.visibility = View.INVISIBLE
                    scheduleDismiss()
                }
                state = it.status
            }) { fabricPrivacy.logException(it) }
        )
    }

    override fun dismiss() {
        aapsLogger.debug(LTag.UI, "dismiss")
        try {
            super.dismiss()
        } catch (e: IllegalStateException) {
            // dialog not running yet. onResume will try again. Set bolusEnded to make extra
            // sure onResume will catch this
            bolusEnded = true
            aapsLogger.error("Unhandled exception", e)
        }
        helpActivity?.finish()
    }

    override fun onPause() {
        super.onPause()
        aapsLogger.debug(LTag.UI, "onPause")
        running = false
        disposable.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("state", state)
        outState.putDouble("amount", amount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    private fun scheduleDismiss() {
        aapsLogger.debug(LTag.UI, "scheduleDismiss")
        Thread {
            SystemClock.sleep(5000)
            bolusEnded = true
            activity?.runOnUiThread {
                if (running) {
                    aapsLogger.debug(LTag.UI, "executing")
                    try {
                        dismiss()
                    } catch (e: Exception) {
                        aapsLogger.error("Unhandled exception", e)
                    }
                }
            }
        }.start()
    }
}
