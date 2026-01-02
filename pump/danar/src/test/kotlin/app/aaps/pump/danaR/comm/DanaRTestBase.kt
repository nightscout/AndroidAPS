package app.aaps.pump.danaR.comm

import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.whenever

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
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setup() {
        danaPump = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)
        doNothing().whenever(danaRKoreanPlugin).setPluginEnabledBlocking(anyOrNull(), anyBoolean())
        doNothing().whenever(danaRPlugin).setPluginEnabledBlocking(anyOrNull(), anyBoolean())
        doNothing().whenever(danaRKoreanPlugin).setFragmentVisible(anyOrNull(), anyBoolean())
        doNothing().whenever(danaRPlugin).setFragmentVisible(anyOrNull(), anyBoolean())
        whenever(rh.gs(ArgumentMatchers.anyInt())).thenReturn("")
    }

    init {
        addInjector {
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