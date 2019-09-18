package info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.VersionResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSetupState;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PairServiceTest {
    @Mock
    private OmnipodCommunicationService communicationService;

    @After
    public void tearDown() {
        verifyNoMoreInteractions(communicationService);
    }

    @Test
    public void testExecuteAssignAddressCommand() {
        // Setup
        PodSetupState setupState = new PodSetupState(0x1f173217, 0x00, 0x00);
        VersionResponse response = mock(VersionResponse.class);
        ArgumentCaptor<OmnipodMessage> messageCaptor = ArgumentCaptor.forClass(OmnipodMessage.class);

        when(communicationService.exchangeMessages(any(), any(), any(), any(), any())).thenReturn(response);

        // SUT
        VersionResponse versionResponse = new PairService().executeAssignAddressCommand(communicationService, setupState);

        // verify
        verify(communicationService).exchangeMessages(eq(VersionResponse.class), eq(setupState), messageCaptor.capture(), eq(OmnipodConst.DEFAULT_ADDRESS), eq(0x1f173217));
        verifyNoMoreInteractions(communicationService);
        verifyZeroInteractions(response);

        assertEquals(versionResponse, response);

        OmnipodMessage message = messageCaptor.getValue();
        byte[] expectedMessage = ByteUtil.fromHexString("ffffffff000607041f17321700fa"); // from https://github.com/openaps/openomni/wiki/Priming-and-Deploying-New-Pod-%28jweismann%29
        assertArrayEquals(expectedMessage, message.getEncoded());
    }

    @Test
    public void testExecuteConfigurePodCommand() {
        // TODO
    }

    // TODO add scenarios
}
