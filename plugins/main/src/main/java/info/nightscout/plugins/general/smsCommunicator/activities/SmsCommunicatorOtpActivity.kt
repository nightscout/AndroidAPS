package info.nightscout.plugins.general.smsCommunicator.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import com.google.common.primitives.Ints.min
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.smsCommunicator.SmsCommunicator
import info.nightscout.plugins.R
import info.nightscout.plugins.databinding.SmscommunicatorActivityOtpBinding
import info.nightscout.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.plugins.general.smsCommunicator.otp.OneTimePasswordValidationResult
import info.nightscout.shared.interfaces.ResourceHelper
import net.glxn.qrgen.android.QRCode
import javax.inject.Inject

class SmsCommunicatorOtpActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var smsCommunicator: SmsCommunicator
    @Inject lateinit var otp: OneTimePassword
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var rh: ResourceHelper

    private lateinit var binding: SmscommunicatorActivityOtpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = SmscommunicatorActivityOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.otpVerifyEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val checkResult = otp.checkOTP(s.toString())

                binding.otpVerifyLabel.text = when (checkResult) {
                    OneTimePasswordValidationResult.OK                 -> "OK"
                    OneTimePasswordValidationResult.ERROR_WRONG_LENGTH -> "INVALID SIZE!"
                    OneTimePasswordValidationResult.ERROR_WRONG_PIN    -> "WRONG PIN"
                    OneTimePasswordValidationResult.ERROR_WRONG_OTP    -> "WRONG OTP"
                }

                binding.otpVerifyLabel.setTextColor(
                    when (checkResult) {
                        OneTimePasswordValidationResult.OK                 -> Color.GREEN
                        OneTimePasswordValidationResult.ERROR_WRONG_LENGTH -> Color.YELLOW
                        OneTimePasswordValidationResult.ERROR_WRONG_PIN    -> Color.RED
                        OneTimePasswordValidationResult.ERROR_WRONG_OTP    -> Color.RED
                    }
                )
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.otpReset.setOnClickListener {
            OKDialog.showConfirmation(
                this,
                rh.gs(R.string.smscommunicator_otp_reset_title),
                rh.gs(R.string.smscommunicator_otp_reset_prompt),
                Runnable {
                    uel.log(Action.OTP_RESET, Sources.SMS)
                    otp.ensureKey(true)
                    updateGui()
                    ToastUtils.Long.infoToast(this, rh.gs(R.string.smscommunicator_otp_reset_successful))
                })
        }

        binding.otpProvisioning.setOnLongClickListener {
            OKDialog.showConfirmation(
                this,
                rh.gs(R.string.smscommunicator_otp_export_title),
                rh.gs(R.string.smscommunicator_otp_export_prompt),
                Runnable {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OTP Secret", otp.provisioningSecret())
                    clipboard.setPrimaryClip(clip)
                    ToastUtils.Long.infoToast(this, rh.gs(R.string.smscommunicator_otp_export_successful))
                    uel.log(Action.OTP_EXPORT, Sources.SMS)
                })

            true
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        updateGui()
    }

    private fun updateGui() {
        val displayMetrics = Resources.getSystem().displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        // ensure QRCode is big enough to fit on screen
        val dim = (min(width, height) * 0.85).toInt()
        val provURI = otp.provisioningURI()

        if (provURI != null) {
            val myBitmap = QRCode.from(provURI).withErrorCorrection(ErrorCorrectionLevel.H).withSize(dim, dim).bitmap()
            binding.otpProvisioning.setImageBitmap(myBitmap)
            binding.otpProvisioning.visibility = View.VISIBLE
        } else {
            binding.otpProvisioning.visibility = View.GONE
        }
    }
}