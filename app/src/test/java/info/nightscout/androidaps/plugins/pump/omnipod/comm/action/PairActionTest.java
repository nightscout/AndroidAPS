package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.PairService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.VersionResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FirmwareVersion;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PairActionTest {
    @Mock
    private PairService pairService;

    @Mock
    private OmnipodCommunicationService communicationService;

    @Mock
    private VersionResponse assignAddressResponse;

    @Mock
    private VersionResponse confirmPairingResponse;

    // FIXME test fails because PodState requires android context to be able to store itself
    // Proposal: storing PodState should happen elsewhere, not in the class itself
    @Ignore
    @Test
    public void testServiceInvocationFromCapture() {
        // Setup
        int address = 0x1f173217;

        FirmwareVersion pmVersion = new FirmwareVersion(1, 2, 3);
        FirmwareVersion piVersion = new FirmwareVersion(4, 5, 6);

        when(assignAddressResponse.getLot()).thenReturn(13);
        when(assignAddressResponse.getTid()).thenReturn(8);

        when(confirmPairingResponse.getLot()).thenReturn(13);
        when(confirmPairingResponse.getTid()).thenReturn(8);
        when(confirmPairingResponse.getPmVersion()).thenReturn(pmVersion);
        when(confirmPairingResponse.getPiVersion()).thenReturn(piVersion);

        when(pairService.executeAssignAddressCommand(eq(communicationService), argThat(setupState -> setupState.getAddress() == address))) //
                .thenReturn(assignAddressResponse);

        when(pairService.executeConfigurePodCommand(eq(communicationService), argThat(setupState -> setupState.getAddress() == address), eq(13), eq(8), any(DateTime.class))) //
                .thenReturn(confirmPairingResponse);

        // SUT
        PodSessionState podState = new PairAction(pairService, address).execute(communicationService);

        // Verify
        verify(pairService).executeAssignAddressCommand(any(), any());
        verify(pairService).executeConfigurePodCommand(any(), any(), anyInt(), anyInt(), any(DateTime.class));

        verifyNoMoreInteractions(pairService);

        // The InitializePodAction should not directly invoke the OmnipodCommunicationService
        // This should be done by the InitializePodService
        verifyZeroInteractions(communicationService);

        Seconds seconds = Seconds.secondsBetween(podState.getActivatedAt(), DateTime.now());

        assertTrue("Expected the pod activation time to be less then 3 seconds ago", seconds.isLessThan(Seconds.seconds(3)));
        assertEquals(13, podState.getLot());
        assertEquals(8, podState.getTid());
        assertEquals(piVersion, podState.getPiVersion());
        assertEquals(pmVersion, podState.getPmVersion());
        //assertEquals(0xTODO, podState.getCurrentNonce());
        //assertEquals(0xTODO, podState.getPacketNumber());
        //assertEquals(0xTODO, podState.getMessageNumber());
    }

    // TODO add scenarios (?)
}
