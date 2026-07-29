package app.aaps.plugins.sync.smsCommunicator.otp

import android.util.Base64
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.Locale

internal class OneTimePasswordTest {

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil

    // Only android.util.Base64 needs faking (ensureKey base64-encodes the generated key); delegate to
    // java.util.Base64 so it round-trips like the real thing.
    private lateinit var base64Mock: MockedStatic<Base64>

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        base64Mock = Mockito.mockStatic(Base64::class.java)
        base64Mock.`when`<String> { Base64.encodeToString(any<ByteArray>(), any<Int>()) }.thenAnswer {
            java.util.Base64.getEncoder().encodeToString(it.getArgument<ByteArray>(0))
        }
        base64Mock.`when`<ByteArray> { Base64.decode(any<String>(), any<Int>()) }.thenAnswer {
            java.util.Base64.getDecoder().decode(it.getArgument<String>(0))
        }
        // Empty secret → ensureKey() generates a fresh SHA1 key (value irrelevant; we only check OTP format).
        whenever(preferences.get(StringNonKey.SmsOtpSecret)).thenReturn("")
        whenever(preferences.get(StringKey.SmsOtpPassword)).thenReturn("1234")
    }

    @AfterEach
    fun tearDown() {
        base64Mock.close()
    }

    @Test
    fun otpIsAsciiDigitsRegardlessOfDeviceLocale() {
        // Regression: with Locale.getDefault() a non-Latin-digit locale (e.g. Persian) rendered the OTP with
        // localized digits, so it never matched the user's Authenticator app. Locale.ROOT must keep it ASCII.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("fa")) // Persian: %06d would otherwise produce ۰-۹
            val otp = OneTimePassword(preferences, rh, dateUtil)

            val token = otp.generateOneTimePassword(1L)

            assertThat(token).hasLength(6)
            assertThat(token.all { it in '0'..'9' }).isTrue()
        } finally {
            Locale.setDefault(original)
        }
    }
}
