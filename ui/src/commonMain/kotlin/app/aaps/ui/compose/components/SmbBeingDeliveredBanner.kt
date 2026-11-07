package app.aaps.ui.compose.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.banner.WarningBanner
import app.aaps.core.ui.compose.stringResource

/**
 * Says why OK is disabled in the bolus dialogs: an SMB is being delivered (#5381). It cannot be stopped, and the dose
 * must be calculated with it. The input stays, and OK works again when the SMB has finished.
 *
 * In the `topBar` slot of the Scaffold, under the app bar, like [RecordOnlyBanner], so no scroll moves it away.
 */
@Composable
fun SmbBeingDeliveredBanner() {
    WarningBanner(
        message = stringResource(CoreUiStrings.smb_being_delivered_confirm_later),
        modifier = Modifier
            .padding(horizontal = AapsSpacing.extraLarge)
            .padding(top = AapsSpacing.medium)
    )
}
