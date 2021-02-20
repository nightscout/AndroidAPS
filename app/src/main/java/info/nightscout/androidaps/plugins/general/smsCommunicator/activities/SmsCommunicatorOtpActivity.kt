package info.nightscout.androidaps.plugins.general.smsCommunicator.activities

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
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.databinding.ActivitySmscommunicatorOtpBinding
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePasswordValidationResult
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import net.glxn.qrgen.android.QRCode
import javax.inject.Inject

class SmsCommunicatorOtpActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var otp: OneTimePassword
    @Inject lateinit var uel: UserEntryLogger

    private lateinit var binding: ActivitySmscommunicatorOtpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivitySmscommunicatorOtpBinding.inflate(layoutInflater)
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

                binding.otpVerifyLabel.setTextColor(when (checkResult) {
                    OneTimePasswordValidationResult.OK                 -> Color.GREEN
                    OneTimePasswordValidationResult.ERROR_WRONG_LENGTH -> Color.YELLOW
                    OneTimePasswordValidationResult.ERROR_WRONG_PIN    -> Color.RED
                    OneTimePasswordValidationResult.ERROR_WRONG_OTP    -> Color.RED
                })
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.otpReset.setOnClickListener {
            OKDialog.showConfirmation(this,
                resourceHelper.gs(R.string.smscommunicator_otp_reset_title),
                resourceHelper.gs(R.string.smscommunicator_otp_reset_prompt),
                Runnable {
                    uel.log("OTP RESET")
                    otp.ensureKey(true)
                    updateGui()
                    ToastUtils.Long.infoToast(this, resourceHelper.gs(R.string.smscommunicator_otp_reset_successful))
                })
        }

        binding.otpProvisioning.setOnLongClickListener {
            OKDialog.showConfirmation(this,
                resourceHelper.gs(R.string.smscommunicator_otp_export_title),
                resourceHelper.gs(R.string.smscommunicator_otp_export_prompt),
                Runnable {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OTP Secret", otp.provisioningSecret())
                    clipboard.primaryClip = clip
                    ToastUtils.Long.infoToast(this, resourceHelper.gs(R.string.smscommunicator_otp_export_successful))
                    uel.log("OTP EXPORT")
                })

            true
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        updateGui()
    }

    fun updateGui() {
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

        binding.otpVerifyEdit.text = binding.otpVerifyEdit.text
    }
}