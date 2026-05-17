package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import javax.inject.Inject

class SWEditEncryptedPassword @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck, private val cryptoUtil: CryptoUtil) :
    SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var onSetPassword: (() -> Unit)? = null

    fun onSetPassword(action: () -> Unit): SWEditEncryptedPassword {
        this.onSetPassword = action
        return this
    }

    fun preference(preference: StringKey): SWEditEncryptedPassword {
        this.preference = preference
        return this
    }

    override fun save(value: CharSequence, updateDelay: Long) {
        preferences.put(preference as StringPreferenceKey, cryptoUtil.hashPassword(value.toString()))
        scheduleChange(updateDelay)
    }

    @Composable
    override fun Compose() {
        val hasPassword = preferences.getIfExists(StringKey.ProtectionMasterPassword).isNullOrEmpty().not()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (hasPassword) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = if (hasPassword) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(AapsSpacing.large))
            Text(
                text = stringResource(if (hasPassword) R.string.password_set else R.string.password_not_set),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { onSetPassword?.invoke() }) {
                Text(stringResource(if (hasPassword) R.string.change else R.string.set))
            }
        }
    }
}
