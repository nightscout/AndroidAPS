package info.nightscout.androidaps.plugins.constraints.storage;
import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Created by Rumen on 06.03.2019.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, StorageConstraintPlugin.class})
public class StorageConstraintPluginTest extends StorageConstraintPlugin{

    StorageConstraintPlugin storageConstraintPlugin;

    @Test
    public void isLoopInvocationAllowedTest(){
        PowerMockito.mockStatic(StorageConstraintPlugin.class);
        // Set free space under 200(Mb) to disable loop
        when(StorageConstraintPlugin.getAvailableInternalMemorySize()).thenReturn(150L);
        Constraint<Boolean> c = new Constraint<>(true);
        c = storageConstraintPlugin.isClosedLoopAllowed(c);
        Assert.assertEquals(true, c.getReasons().contains(MainApp.gs(R.string.diskfull)));
        Assert.assertEquals(Boolean.FALSE, c.value());
        // Set free space over 200(Mb) to enable loop
        when(StorageConstraintPlugin.getAvailableInternalMemorySize()).thenReturn(300L);
        Constraint<Boolean> c2 = new Constraint<>(true);
        c2 = storageConstraintPlugin.isClosedLoopAllowed(c2);
        Assert.assertEquals(false, c2.getReasons().contains(MainApp.gs(R.string.diskfull)));
        Assert.assertEquals(Boolean.TRUE, c2.value());
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
//        PowerMockito.mockStatic(Environment.class);
        storageConstraintPlugin = StorageConstraintPlugin.getPlugin();
    }
}
