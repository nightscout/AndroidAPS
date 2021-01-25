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
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePasswordValidationResult
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import kotlinx.android.synthetic.main.activity_smscommunicator_otp.*
import net.glxn.qrgen.android.QRCode
import javax.inject.Inject

class SmsCommunicatorOtpActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var otp: OneTimePassword

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_smscommunicator_otp)

        smscommunicator_otp_verify_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val checkResult = otp.checkOTP(s.toString())

                smscommunicator_otp_verify_label.text = when (checkResult) {
                    OneTimePasswordValidationResult.OK                 -> "OK"
                    OneTimePasswordValidationResult.ERROR_WRONG_LENGTH -> "INVALID SIZE!"
                    OneTimePasswordValidationResult.ERROR_WRONG_PIN    -> "WRONG PIN"
                    OneTimePasswordValidationResult.ERROR_WRONG_OTP    -> "WRONG OTP"
                }

                smscommunicator_otp_verify_label.setTextColor(when (checkResult) {
                    OneTimePasswordValidationResult.OK                 -> Color.GREEN
                    OneTimePasswordValidationResult.ERROR_WRONG_LENGTH -> Color.YELLOW
                    OneTimePasswordValidationResult.ERROR_WRONG_PIN    -> Color.RED
                    OneTimePasswordValidationResult.ERROR_WRONG_OTP    -> Color.RED
                })
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        actions_smscommunicator_otp_reset.setOnClickListener {
            OKDialog.showConfirmation(this,
                resourceHelper.gs(R.string.smscommunicator_otp_reset_title),
                resourceHelper.gs(R.string.smscommunicator_otp_reset_prompt),
                Runnable {
                    otp.ensureKey(true)
                    updateGui()
                    ToastUtils.Long.infoToast(this, resourceHelper.gs(R.string.smscommunicator_otp_reset_successful))
                })
        }

        smscommunicator_otp_provisioning.setOnLongClickListener {
            OKDialog.showConfirmation(this,
                resourceHelper.gs(R.string.smscommunicator_otp_export_title),
                resourceHelper.gs(R.string.smscommunicator_otp_export_prompt),
                Runnable {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OTP Secret", otp.provisioningSecret())
                    clipboard.primaryClip = clip
                    ToastUtils.Long.infoToast(this, resourceHelper.gs(R.string.smscommunicator_otp_export_successful))
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
            smscommunicator_otp_provisioning.setImageBitmap(myBitmap)
            smscommunicator_otp_provisioning.visibility = View.VISIBLE
        } else {
            smscommunicator_otp_provisioning.visibility = View.GONE
        }

        smscommunicator_otp_verify_edit.text = smscommunicator_otp_verify_edit.text
    }
}