package ruffyscripter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.monkey.d.ruffy.ruffy.driver.IRuffyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.jotomo.ruffyscripter.RuffyScripter;
import de.jotomo.ruffyscripter.commands.CommandResult;
import de.jotomo.ruffyscripter.commands.ReadPumpStateCommand;
import info.nightscout.androidaps.MainApp;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class RuffyScripterInstrumentedTest {
    private static final Logger log = LoggerFactory.getLogger(RuffyScripter.class);

    private static Context appContext = InstrumentationRegistry.getTargetContext();
    private static ServiceConnection mRuffyServiceConnection;
    private static RuffyScripter ruffyScripter;

    @BeforeClass
    public static void bindRuffy() {
        Intent intent = new Intent()
                .setComponent(new ComponentName(
                        // this must be the base package of the app (check package attribute in
                        // manifest element in the manifest file of the providing app)
                        "org.monkey.d.ruffy.ruffy",
                        // full path to the driver
                        // in the logs this service is mentioned as (note the slash)
                        // "org.monkey.d.ruffy.ruffy/.driver.Ruffy"
                        "org.monkey.d.ruffy.ruffy.driver.Ruffy"
                ));
        appContext.startService(intent);

        mRuffyServiceConnection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                ruffyScripter = new RuffyScripter(IRuffyService.Stub.asInterface(service));
                log.debug("ruffy serivce connected");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                log.debug("ruffy service disconnected");
            }
        };

        boolean success = appContext.bindService(intent, mRuffyServiceConnection, Context.BIND_AUTO_CREATE);
        if (!success) {
            log.error("Binding to ruffy service failed");
        }

        long timeout = System.currentTimeMillis() + 60 * 1000;
        while (ruffyScripter == null) {
            SystemClock.sleep(500);
            log.debug("Waiting for ruffy service connection");
            long now = System.currentTimeMillis();
            if (now > timeout) {
                throw new RuntimeException("Ruffy service could not be bound");
            }

        }
    }

    @AfterClass
    public static void unbindRuffy() {
       appContext.unbindService(mRuffyServiceConnection);
    }

    // TODO now, how to get ruffy fired up in this test?
    @Test
    public void readPumpState() throws Exception {
        CommandResult commandResult = ruffyScripter.runCommand(new ReadPumpStateCommand());
        assertTrue(commandResult.success);
        assertFalse(commandResult.enacted);
        assertNotNull(commandResult.state);
    }
}
