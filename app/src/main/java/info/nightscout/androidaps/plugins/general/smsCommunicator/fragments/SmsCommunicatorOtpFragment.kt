package info.nightscout.androidaps.plugins.general.smsCommunicator.fragments

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.primitives.Ints.min
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.OneTimePassword
import info.nightscout.androidaps.utils.OneTimePasswordValidationResult
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.smscommunicator_fragment_otp.*
import net.glxn.qrgen.android.QRCode
import javax.inject.Inject

class SmsCommunicatorOtpFragment : DaggerFragment() {
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var otp: OneTimePassword
    @Inject lateinit var resourceHelper: ResourceHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.smscommunicator_fragment_otp, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        smscommunicator_otp_verify_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s != null) {
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

                } else {
                    smscommunicator_otp_verify_label.text = "EMPTY";
                    smscommunicator_otp_verify_label.setTextColor(Color.YELLOW)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

        actions_smscommunicator_otp_reset.setOnClickListener {
            if (this.activity != null) {
                OKDialog.showConfirmation(this.activity!!,
                    resourceHelper.gs(R.string.smscommunicator_otp_reset_title),
                    resourceHelper.gs(R.string.smscommunicator_otp_reset_prompt),
                    Runnable {
                        otp.ensureKey(true)
                        updateGui()
                        ToastUtils.showToastInUiThread(this.context, R.string.smscommunicator_otp_reset_successful)
                    })
            }
        }

    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        updateGui()
    }

    fun updateGui() {
        val displayMetrics = Resources.getSystem().getDisplayMetrics()
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        // ensure QRCode is big enough to fit on screen
        val dim = (min(width, height) * 0.85).toInt()
        val provURI = otp.provisioningURI();

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