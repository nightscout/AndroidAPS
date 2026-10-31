package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins what the master password step of the setup wizard tells the user, and that the password it
 * stores is hashed. Written before the file is considered for a move to commonMain, so the same
 * assertions can be re-run afterwards unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SWEditEncryptedPasswordTest {

    @get:Rule
    val compose = createComposeRule()

    private val preferences: Preferences = mock<Preferences>()
    private val cryptoUtil: CryptoUtil = mock<CryptoUtil>()

    private lateinit var passwordSetText: String
    private lateinit var passwordNotSetText: String
    private lateinit var changeText: String
    private lateinit var setText: String

    private fun newItem(): SWEditEncryptedPassword =
        SWEditEncryptedPassword(
            aapsLogger = mock<AAPSLogger>(),
            rh = mock<TextResolver>(),
            rxBus = mock<RxBus>(),
            preferences = preferences,
            passwordCheck = mock<PasswordCheck>(),
            cryptoUtil = cryptoUtil
        )

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        passwordSetText = app.getString(R.string.password_set)
        passwordNotSetText = app.getString(R.string.password_not_set)
        changeText = app.getString(R.string.change)
        setText = app.getString(R.string.set)
    }

    private fun storedPassword(value: String?) {
        whenever(preferences.getIfExists(StringKey.ProtectionMasterPassword)).thenReturn(value)
    }

    @Test
    fun noPasswordStored_saysNotSetAndOffersSet() {
        storedPassword(null)
        val item = newItem()

        compose.setContent { MaterialTheme { item.Compose() } }

        compose.onNodeWithText(passwordNotSetText).assertIsDisplayed()
        compose.onNodeWithText(setText).assertIsDisplayed()
    }

    @Test
    fun emptyPasswordStored_saysNotSet() {
        storedPassword("")
        val item = newItem()

        compose.setContent { MaterialTheme { item.Compose() } }

        compose.onNodeWithText(passwordNotSetText).assertIsDisplayed()
        compose.onNodeWithText(setText).assertIsDisplayed()
    }

    @Test
    fun passwordStored_saysSetAndOffersChange() {
        storedPassword("hmac:abcd:efgh")
        val item = newItem()

        compose.setContent { MaterialTheme { item.Compose() } }

        compose.onNodeWithText(passwordSetText).assertIsDisplayed()
        compose.onNodeWithText(changeText).assertIsDisplayed()
    }

    @Test
    fun clickingButton_runsOnSetPassword() {
        storedPassword(null)
        var runs = 0
        val item = newItem().onSetPassword { runs++ }

        compose.setContent { MaterialTheme { item.Compose() } }
        compose.onNodeWithText(setText).performClick()

        assert(runs == 1) { "expected onSetPassword to run once, ran $runs times" }
    }

    @Test
    fun save_storesHashedValueNeverThePlainText() {
        whenever(cryptoUtil.hashPassword("secret")).thenReturn("hmac:salt:digest")
        val item = newItem().preference(StringKey.ProtectionMasterPassword)

        item.save("secret", updateDelay = 0)

        verify(preferences).put(StringKey.ProtectionMasterPassword, "hmac:salt:digest")
        verify(preferences, never()).put(StringKey.ProtectionMasterPassword, "secret")
    }
}
