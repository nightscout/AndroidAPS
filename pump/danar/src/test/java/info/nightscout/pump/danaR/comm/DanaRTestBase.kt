package info.nightscout.pump.danaR.comm

import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.shared.tests.TestBaseWithProfile
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.dana.database.DanaHistoryRecordDao
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doNothing

open class DanaRTestBase : TestBaseWithProfile() {

    @Mock lateinit var danaRPlugin: DanaRPlugin
    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Mock lateinit var danaRv2Plugin: DanaRv2Plugin
    @Mock lateinit var configBuilder: ConfigBuilder
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var danaHistoryRecordDao: DanaHistoryRecordDao
    @Mock lateinit var instantiator: Instantiator
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setup() {
        danaPump = DanaPump(aapsLogger, sp, dateUtil, instantiator, decimalFormatter)
        doNothing().`when`(danaRKoreanPlugin).setPluginEnabled(anyObject(), anyBoolean())
        doNothing().`when`(danaRPlugin).setPluginEnabled(anyObject(), anyBoolean())
        doNothing().`when`(danaRKoreanPlugin).setFragmentVisible(anyObject(), anyBoolean())
        doNothing().`when`(danaRPlugin).setFragmentVisible(anyObject(), anyBoolean())
        `when`(rh.gs(ArgumentMatchers.anyInt())).thenReturn("")
    }

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is MessageBase) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.danaPump = danaPump
                it.danaRPlugin = danaRPlugin
                it.danaRKoreanPlugin = danaRKoreanPlugin
                it.danaRv2Plugin = danaRv2Plugin
                it.rxBus = rxBus
                it.rh = rh
                it.activePlugin = activePlugin
                it.configBuilder = configBuilder
                it.detailedBolusInfoStorage = detailedBolusInfoStorage
                it.constraintChecker = constraintChecker
                it.commandQueue = commandQueue
                it.pumpSync = pumpSync
                it.danaHistoryRecordDao = danaHistoryRecordDao
                it.uiInteraction = uiInteraction
            }
        }
    }

    lateinit var danaPump: DanaPump

    fun createArray(length: Int, fillWith: Byte): ByteArray {
        val ret = ByteArray(length)
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }

    fun createArray(length: Int, fillWith: Double): Array<Double> {
        val ret = Array(length) { 0.0 }
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }

    fun putIntToArray(array: ByteArray, position: Int, value: Int): ByteArray {
        array[6 + position + 1] = (value and 0xFF).toByte()
        array[6 + position] = ((value and 0xFF00) shr 8).toByte()
        return array
    }

    fun putByteToArray(array: ByteArray, position: Int, value: Byte): ByteArray {
        array[6 + position] = value
        return array
    }
}