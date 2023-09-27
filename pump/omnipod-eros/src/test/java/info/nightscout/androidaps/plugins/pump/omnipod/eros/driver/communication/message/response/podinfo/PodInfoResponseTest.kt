package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodInfoType
import info.nightscout.pump.common.utils.ByteUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class PodInfoResponseTest {

    @Test fun testRawData() {
        val encodedData = ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d")
        val podInfoResponse = PodInfoResponse(encodedData)
        Assertions.assertArrayEquals(encodedData, podInfoResponse.rawData)
    }

    @Test fun testRawDataWithLongerMessage() {
        val encodedData = ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d01")
        val expected = ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d")
        val podInfoResponse = PodInfoResponse(encodedData)
        Assertions.assertArrayEquals(expected, podInfoResponse.rawData)
    }

    @Test fun testMessageDecoding() {
        val podInfoResponse = PodInfoResponse(ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d"))
        Assertions.assertEquals(PodInfoType.DETAILED_STATUS, podInfoResponse.subType)
        val podInfo = podInfoResponse.podInfo as PodInfoDetailedStatus
        Assertions.assertFalse(podInfo.isFaultAccessingTables)
        Assertions.assertEquals(0x01, podInfo.errorEventInfo.internalVariable.toInt())
    }

    @Test fun testInvalidPodInfoTypeMessageDecoding() {
        val podInfoResponse = PodInfoResponse(ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d"))
        Assertions.assertEquals(PodInfoType.DETAILED_STATUS, podInfoResponse.subType)
        assertThat(podInfoResponse.podInfo).isNotInstanceOf(PodInfoActiveAlerts::class.java)
    }
}
