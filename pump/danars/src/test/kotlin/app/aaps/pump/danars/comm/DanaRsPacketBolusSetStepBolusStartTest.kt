package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

class DanaRsPacketBolusSetStepBolusStartTest : DanaRSTestBase() {

    @Mock lateinit var constraintChecker: ConstraintsChecker

    @Test fun runTest() {
        val packet = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump, constraintChecker)
        // test params
        val testParams = packet.getRequestParams()
        Assertions.assertEquals(0.toByte(), testParams[0])
        Assertions.assertEquals(0.toByte(), testParams[2])
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BOLUS__SET_STEP_BOLUS_START", packet.friendlyName)
    }

    @BeforeEach
    fun mock() {
        whenever(constraintChecker.applyBolusConstraints(anyOrNull())).thenReturn(ConstraintObject(0.0, aapsLogger))
    }
}