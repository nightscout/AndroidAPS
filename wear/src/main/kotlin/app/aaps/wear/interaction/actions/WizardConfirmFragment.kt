package app.aaps.wear.interaction.actions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.wear.activity.ConfirmationActivity
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import dagger.android.support.AndroidSupportInjection
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.text.DecimalFormat
import javax.inject.Inject

class WizardConfirmFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus

    private val decimalFormat = DecimalFormat("0.00")
    private val disposable = CompositeDisposable()
    private var timestamp: Long = 0
    private var confirmationSent = false

    companion object {
        private const val ARG_TIMESTAMP = "timestamp"
        private const val ARG_TOTAL_INSULIN = "total_insulin"
        private const val ARG_CARBS = "carbs"

        fun newInstance(timestamp: Long, totalInsulin: Double, carbs: Int): WizardConfirmFragment {
            return WizardConfirmFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TIMESTAMP, timestamp)
                    putDouble(ARG_TOTAL_INSULIN, totalInsulin)
                    putInt(ARG_CARBS, carbs)
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_wizard_confirm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return

        timestamp = args.getLong(ARG_TIMESTAMP)
        val totalInsulin = args.getDouble(ARG_TOTAL_INSULIN)
        val carbs = args.getInt(ARG_CARBS)

        view.findViewById<TextView>(R.id.confirm_total_insulin).text =
            getString(R.string.wizard_insulin_format, decimalFormat.format(totalInsulin))
        view.findViewById<TextView>(R.id.confirm_carbs).text =
            getString(R.string.wizard_carbs_format, carbs)

        view.findViewById<ImageView>(R.id.confirm_button).setOnClickListener { button ->
            if (confirmationSent) return@setOnClickListener
            confirmationSent = true
            button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            button.isClickable = false

            // Send confirmation to phone
            rxBus.send(EventWearToMobile(EventData.ActionWizardConfirmed(timestamp)))

            // Show success confirmation
            showConfirmation()
        }
    }

    private fun showConfirmation() {
        if (!isAdded || isDetached) return  // Safety check

        val intent = Intent(requireContext(), ConfirmationActivity::class.java).apply {
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.wizard_confirmation_sent))
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
    }
}