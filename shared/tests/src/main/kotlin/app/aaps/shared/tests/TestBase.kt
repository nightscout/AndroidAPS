package app.aaps.shared.tests

import android.annotation.SuppressLint
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.shared.tests.rx.TestAapsSchedulers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatcher
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.Locale

@Suppress("SpellCheckingInspection")
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
open class TestBase {

    val aapsLogger = AAPSLoggerTest()
    val aapsSchedulers: AapsSchedulers = TestAapsSchedulers()
    lateinit var rxBus: RxBus

    @BeforeEach
    fun setupLocale() {
        Locale.setDefault(Locale.ENGLISH)
        System.setProperty("disableFirebase", "true")
        rxBus = RxBusImpl(aapsSchedulers, aapsLogger)
    }

    @SuppressLint("CheckResult")
    fun <T> argThatKotlin(matcher: ArgumentMatcher<T>): T {
        Mockito.argThat(matcher)
        return uninitialized()
    }

    @SuppressLint("CheckResult")
    fun <T> eqObject(expected: T): T {
        Mockito.eq<T>(expected)
        return uninitialized()
    }

    // Workaround for Kotlin nullability.
    // https://medium.com/@elye.project/befriending-kotlin-and-mockito-1c2e7b0ef791
    // https://stackoverflow.com/questions/30305217/is-it-possible-to-use-mockito-in-kotlin
    @SuppressLint("CheckResult")
    fun <T> anyObject(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    @Suppress("Unchecked_Cast")
    private fun <T> uninitialized(): T = null as T
}