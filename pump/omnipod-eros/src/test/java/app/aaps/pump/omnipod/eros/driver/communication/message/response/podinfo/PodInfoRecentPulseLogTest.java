package app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.aaps.core.utils.pump.ByteUtil;

class PodInfoRecentPulseLogTest {
    @Test
    void testDecoding() {
        PodInfoRecentPulseLog podInfoRecentPulseLog = new PodInfoRecentPulseLog(ByteUtil.INSTANCE.fromHexString("3d313b004030350045303a00483033004d313a005031310054313f00583038805d302d806030368001313b800c3033801130388014313480193138801c313280213039802431360029313d002c31390031303f0034313900393140003c31390041313e00443137004905723a80087335800d733a801073358015733a80187235801d7338802073338025733a00287235002d723b003072360035703b00383134"), 160);

        Assertions.assertEquals(39, podInfoRecentPulseLog.getDwords().size());
    }
}
