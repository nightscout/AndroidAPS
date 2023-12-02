package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodInfoType
import info.nightscout.pump.common.utils.ByteUtil
import kotlin.test.assertIsNot
import org.junit.jupiter.api.Test

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
        val podInfoResponse = PodInfoResponse(ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d"))
        assertThat(podInfoResponse.subType).isEqualTo(PodInfoType.DETAILED_STATUS)
        val podInfo = podInfoResponse.podInfo as PodInfoDetailedStatus
        assertThat(podInfo.isFaultAccessingTables).isFalse()
        assertThat(podInfo.errorEventInfo.internalVariable.toInt()).isEqualTo(0x01)
    }

    @Test fun testInvalidPodInfoTypeMessageDecoding() {
        val podInfoResponse = PodInfoResponse(ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d"))
        assertThat(podInfoResponse.subType).isEqualTo(PodInfoType.DETAILED_STATUS)
        assertIsNot<PodInfoActiveAlerts>(podInfoResponse.podInfo)
    }
}
