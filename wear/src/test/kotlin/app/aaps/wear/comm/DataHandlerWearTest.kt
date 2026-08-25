package app.aaps.wear.comm

import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.R
import app.aaps.wear.WearTestBase
import app.aaps.wear.data.ComplicationDataRepository
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Drives [DataHandlerWear]'s message handlers through a REAL [RxBusImpl] (trampoline scheduler, so
 * delivery is synchronous). The `Preferences` handler writes to sp/preferences unconditionally — only
 * the tile-refresh side runs when `wearControl` changes, so sending it with an unchanged wearControl
 * keeps the handler Android-free and assertable. Construction touches no Android, so no Robolectric.
 */
internal class DataHandlerWearTest : WearTestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var complicationDataRepository: ComplicationDataRepository
    @Mock lateinit var aapsSchedulers: AapsSchedulers

    private val logger = AAPSLoggerTest()
    private lateinit var rxBus: RxBus
    private lateinit var sut: DataHandlerWear

    @BeforeEach
    fun setupHandler() {
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        rxBus = RxBusImpl(aapsSchedulers, logger)
        sut = DataHandlerWear(context, rxBus, aapsSchedulers, sp, preferences, logger, complicationDataRepository)
    }

    @Test
    fun `preferences event stores the wear settings`() {
        // wearControl (false) equals the mock preferences default, so the tile-refresh branch is skipped.
        rxBus.send(EventData.Preferences(0L, false, true, 50, 80, 25.0, 0.5, 1.0, 5, 10))

        verify(sp).putBoolean(R.string.key_units_mgdl, true)
        verify(sp).putInt(R.string.key_bolus_wizard_percentage, 50)
        verify(sp).putInt(R.string.key_treatments_safety_max_carbs, 80)
        verify(sp).putDouble(R.string.key_treatments_safety_max_bolus, 25.0)
        verify(preferences).put(DoubleKey.OverviewInsulinButtonIncrement1, 0.5)
        verify(preferences).put(DoubleKey.OverviewInsulinButtonIncrement2, 1.0)
        verify(preferences).put(IntKey.OverviewCarbsButtonIncrement1, 5)
        verify(preferences).put(IntKey.OverviewCarbsButtonIncrement2, 10)
    }

    @Test
    fun `a ping is answered with a pong to the mobile`() {
        var pong: EventData.ActionPong? = null
        rxBus.toObservable(EventWearToMobile::class.java).subscribe { evt ->
            (evt.payload as? EventData.ActionPong)?.let { pong = it }
        }

        rxBus.send(EventData.ActionPing(1_000L))

        assertThat(pong).isNotNull()
    }
}
