package info.nightscout.androidaps.plugins.Source;

import android.os.Bundle;
import android.support.annotation.NonNull;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;

import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, DatabaseHelper.class})
public class SourceXdripPluginTest {

    private SourceXdripPlugin plugin = SourceXdripPlugin.getPlugin();

    @Before
    public void prepareTest() {
        PowerMockito.mockStatic(MainApp.class);
        when(MainApp.gs(0)).thenReturn("");
        DatabaseHelper databaseHelper = mock(DatabaseHelper.class);
        when(MainApp.getDbHelper()).thenReturn(databaseHelper);
        when(databaseHelper.createIfNotExists(any(), any())).thenReturn(true);
    }

    @Test
    public void pluginInitializes() {
        Assert.assertNotEquals(null, SourceXdripPlugin.getPlugin());
    }

    // TODO
    @Ignore("Bundle needs to be properly mocked or Robolectrics issues with SQLite resolved")
    @Test
    public void bgWithUnknownSourceIsMarkedUnfiltered() {
        Bundle bundle = createBroadcastBundle();
        BgReading bgReadings = plugin.processNewData(bundle).get(0);
        assertFalse(bgReadings.isFiltered);
    }

    // TODO
    @Ignore("Bundle needs to be properly mocked or Robolectrics issues with SQLite resolved")
    @Test
    public void bgWithSourceG5NativeIsMarkedFiltered() {
        Bundle bundle = createBroadcastBundle();
        bundle.putString(Intents.XDRIP_DATA_SOURCE_DESCRIPTION, "G5 Native");

        BgReading bgReadings = plugin.processNewData(bundle).get(0);
        assertTrue(bgReadings.isFiltered);
    }

    /*
    // TODO
    @Ignore("Bundle needs to be properly mocked or Robolectrics issues with SQLite resolved")
    @Test
    public void bgWithWithGoodNoiseIsMarkedFiltered() {
        Bundle bundle = createBroadcastBundle();
        bundle.putString(Intents.EXTRA_NOISE, "1.0");

        BgReading bgReadings = plugin.processNewData(bundle).get(0);
        assertTrue(bgReadings.isFiltered);
    }

    // TODO
    @Ignore("Bundle needs to be properly mocked or Robolectrics issues with SQLite resolved")
    @Test
    public void bgWithWithExcessiveNoiseDataIsMarkedFiltered() {
        Bundle bundle = createBroadcastBundle();
        bundle.putString(Intents.EXTRA_NOISE, "80.0");

        BgReading bgReadings = plugin.processNewData(bundle).get(0);
        assertTrue(bgReadings.isFiltered);
    }
    */

    @NonNull
    private Bundle createBroadcastBundle() {
        Bundle bundle = new Bundle();
        bundle.putDouble(Intents.EXTRA_BG_ESTIMATE, 100.0);
        bundle.putString(Intents.EXTRA_BG_SLOPE_NAME, "DoubleDown");
        bundle.putLong(Intents.EXTRA_TIMESTAMP, 0L);
        bundle.putDouble(Intents.EXTRA_RAW, 430.0);
        return bundle;
    }
}