package app.aaps.plugins.main.general.smsCommunicator.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalRxBus
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.smsCommunicator.compose.SmsCommunicatorOtpScreen
import app.aaps.plugins.main.general.smsCommunicator.otp.OneTimePassword
import javax.inject.Inject

class SmsCommunicatorOtpComposeActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var otp: OneTimePassword
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        title = rh.gs(R.string.smscommunicator_tab_otp_label)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    CompositionLocalProvider(
                        LocalPreferences provides preferences,
                        LocalRxBus provides rxBus
                    ) {
                        AapsTheme {
                            SmsCommunicatorOtpScreen(
                                otp = otp,
                                onReset = { handleReset() },
                                onExportSecret = { handleExport() }
                            )
                        }
                    }
                }
            }
        )
    }

    private fun handleReset() {
        uiInteraction.showOkCancelDialog(
            context = this,
            title = rh.gs(R.string.smscommunicator_otp_reset_title),
            message = rh.gs(R.string.smscommunicator_otp_reset_prompt),
            ok = {
                uel.log(Action.OTP_RESET, Sources.SMS)
                otp.ensureKey(true)
                ToastUtils.Long.infoToast(this, rh.gs(R.string.smscommunicator_otp_reset_successful))
            }
        )
    }

    private fun handleExport() {
        uiInteraction.showOkCancelDialog(
            context = this,
            title = rh.gs(R.string.smscommunicator_otp_export_title),
            message = rh.gs(R.string.smscommunicator_otp_export_prompt),
            ok = {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OTP Secret", otp.provisioningSecret())
                clipboard.setPrimaryClip(clip)
                ToastUtils.Long.infoToast(this, rh.gs(R.string.smscommunicator_otp_export_successful))
                uel.log(Action.OTP_EXPORT, Sources.SMS)
            }
        )
    }
}
