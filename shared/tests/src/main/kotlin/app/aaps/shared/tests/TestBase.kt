package app.aaps.shared.tests

import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.shared.tests.rx.TestAapsSchedulers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.Locale

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
open class TestBase {

    val aapsLogger = AAPSLoggerTest()
    val aapsSchedulers: AapsSchedulers = TestAapsSchedulers()
    lateinit var rxBus: RxBus

    @BeforeEach
    fun prepareMocking() {
        MockitoAnnotations.openMocks(this)
        Locale.setDefault(Locale.ENGLISH)
        System.setProperty("disableFirebase", "true")
        rxBus = RxBusImpl(aapsSchedulers, aapsLogger)
    }

    @AfterEach
    fun tearDownBase() {
        // Explicitly clear inline mocks
        Mockito.framework().clearInlineMocks()
    }
}