package app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo

import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.omnipod.eros.driver.definition.PodInfoType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertIsNot

internal class PodInfoResponseTest {

    @Test fun testRawData() {
        val encodedData = ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d")!!
        val podInfoResponse = PodInfoResponse(encodedData)
        assertThat(podInfoResponse.rawData.asList()).containsExactlyElementsIn(encodedData.asIterable()).inOrder()
    }

    @Test fun testRawDataWithLongerMessage() {
        val encodedData = ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d01")!!
        val expected = ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d")!!
        val podInfoResponse = PodInfoResponse(encodedData)
        assertThat(podInfoResponse.rawData.asList()).containsExactlyElementsIn(expected.asIterable()).inOrder()
    }

    @Test fun testMessageDecoding() {
        val podInfoResponse = PodInfoResponse(ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d")!!)
        assertThat(podInfoResponse.subType).isEqualTo(PodInfoType.DETAILED_STATUS)
        val podInfo = podInfoResponse.podInfo as PodInfoDetailedStatus
        assertThat(podInfo.isFaultAccessingTables).isFalse()
        assertThat(podInfo.errorEventInfo.internalVariable.toInt()).isEqualTo(0x01)
    }

    @Test fun testInvalidPodInfoTypeMessageDecoding() {
        val podInfoResponse = PodInfoResponse(ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d")!!)
        assertThat(podInfoResponse.subType).isEqualTo(PodInfoType.DETAILED_STATUS)
        assertIsNot<PodInfoActiveAlerts>(podInfoResponse.podInfo)
    }
}
