package info.nightscout.androidaps.plugins.constraints.storage;
import android.os.Environment;
import android.os.StatFs;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.whenNew;
import static org.mockito.ArgumentMatchers.any;

/**
 * Created by Rumen on 06.03.2019.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, StorageConstraintPlugin.class, StatFs.class, Environment.class})
public class StorageConstraintPluginTest extends StorageConstraintPlugin{

    StorageConstraintPlugin storageConstraintPlugin;
    private File mockedFile;
    private static final String path = "/data";
    private StatFs mockedStatFs;

    @Test
    public void isLoopInvocationAllowedTest(){
        PowerMockito.mockStatic(StorageConstraintPlugin.class);
        // Set free space under 200(Mb) to disable loop
        when(StorageConstraintPlugin.getAvailableInternalMemorySize()).thenReturn(150L);
        Constraint<Boolean> c = new Constraint<>(true);
        c = storageConstraintPlugin.isClosedLoopAllowed(c);
        Assert.assertEquals(Boolean.FALSE, c.value());
        // Set free space over 200(Mb) to enable loop
        when(StorageConstraintPlugin.getAvailableInternalMemorySize()).thenReturn(300L);
        Constraint<Boolean> c2 = new Constraint<>(true);
        c2 = storageConstraintPlugin.isClosedLoopAllowed(c2);
        Assert.assertEquals(Boolean.TRUE, c2.value());
    }

    @Test
    public void getAvailableInternalMemorySizeTest() throws Exception {
        PowerMockito.mockStatic(Environment.class);
        PowerMockito.when(Environment.getDataDirectory()).thenReturn(mockedFile);
        when(mockedFile.getPath()).thenReturn(path);
        when(mockedFile.exists()).thenReturn(true);

        whenNew(StatFs.class).withArguments(any()).thenReturn(mockedStatFs);
        when(mockedStatFs.getBlockSizeLong()).thenReturn(1024L);
        when(mockedStatFs.getAvailableBlocksLong()).thenReturn(150l*1024);

        long freeSpaceInMb = storageConstraintPlugin.getAvailableInternalMemorySize();
        Assert.assertEquals(150L, freeSpaceInMb);

    }


    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
        AAPSMocker.mockBus();
        mockedFile = mock(File.class);
        mockedStatFs = mock(StatFs.class);
        storageConstraintPlugin = StorageConstraintPlugin.getPlugin();
    }
}
