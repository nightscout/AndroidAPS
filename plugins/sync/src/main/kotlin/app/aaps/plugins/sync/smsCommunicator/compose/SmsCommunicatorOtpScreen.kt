package app.aaps.plugins.sync.smsCommunicator.compose

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.otp.OneTimePassword
import app.aaps.plugins.sync.smsCommunicator.otp.OneTimePasswordValidationResult
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import net.glxn.qrgen.android.QRCode
import kotlin.math.min

@Composable
internal fun SmsCommunicatorOtpScreen(
    otp: OneTimePassword,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Prevent screenshots of TOTP QR code
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    var verifyText by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf(generateQrBitmap(otp)) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(R.string.smscommunicator_tab_otp_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)
                .clearFocusOnTap(focusManager),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            // Step 1: Install
            SectionHeader(stringResource(R.string.smscommunicator_otp_step1_install_header))
            Text(
                text = stringResource(R.string.smscommunicator_otp_install_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Step 2: QR Code
            SectionHeader(stringResource(R.string.smscommunicator_otp_step2_provisioning_header))
            val qrSizeDp = with(LocalConfiguration.current) {
                min(screenWidthDp, screenHeightDp) * 0.85
            }.toInt().dp
            qrBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.a11y_otp_qr_code),
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(qrSizeDp)
                        .align(Alignment.CenterHorizontally)
                )
            }

            // Step 3: Verify
            SectionHeader(stringResource(R.string.smscommunicator_otp_step3_test_header))
            Text(
                text = stringResource(R.string.smscommunicator_code_verify_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            val checkResult = remember(verifyText) { otp.checkOTP(verifyText) }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                Text(
                    text = stringResource(R.string.smscommunicator_code_verify_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = verifyText,
                    onValueChange = { if (it.length <= 12) verifyText = it },
                    placeholder = { Text(stringResource(R.string.smscommunicator_code_verify_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                if (verifyText.isNotEmpty()) {
                    Text(
                        text = when (checkResult) {
                            OneTimePasswordValidationResult.OK                 -> stringResource(R.string.smscommunicator_otp_verification_ok)
                            OneTimePasswordValidationResult.ERROR_WRONG_LENGTH -> stringResource(R.string.smscommunicator_otp_verification_ivalid_size)
                            OneTimePasswordValidationResult.ERROR_WRONG_PIN    -> stringResource(R.string.smscommunicator_otp_verification_wrong_pin)
                            OneTimePasswordValidationResult.ERROR_WRONG_OTP    -> stringResource(R.string.smscommunicator_otp_verification_wrong_otp)
                        },
                        color = when (checkResult) {
                            OneTimePasswordValidationResult.OK                 -> MaterialTheme.colorScheme.primary
                            OneTimePasswordValidationResult.ERROR_WRONG_LENGTH -> MaterialTheme.colorScheme.tertiary
                            OneTimePasswordValidationResult.ERROR_WRONG_PIN    -> MaterialTheme.colorScheme.error
                            OneTimePasswordValidationResult.ERROR_WRONG_OTP    -> MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Step 4: Reset
            SectionHeader(stringResource(R.string.smscommunicator_otp_reset_header))
            Text(
                text = stringResource(R.string.smscommunicator_otp_reset_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Button(
                onClick = { showResetConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = stringResource(R.string.smscommunicator_otp_reset_btn),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showResetConfirmation) {
        OkCancelDialog(
            title = stringResource(R.string.smscommunicator_otp_reset_title),
            message = stringResource(R.string.smscommunicator_otp_reset_prompt),
            onConfirm = {
                showResetConfirmation = false
                onReset()
                qrBitmap = generateQrBitmap(otp)
            },
            onDismiss = { showResetConfirmation = false }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AapsSpacing.medium)
    )
}

private fun generateQrBitmap(otp: OneTimePassword): Bitmap? =
    otp.provisioningURI()?.let { uri ->
        QRCode.from(uri).withErrorCorrection(ErrorCorrectionLevel.H).withSize(512, 512).bitmap()
    }
