package de.jotomo.ruffyscripter;

import android.os.RemoteException;
import android.os.SystemClock;

import com.google.common.base.Joiner;

import org.monkey.d.ruffy.ruffy.driver.IRTHandler;
import org.monkey.d.ruffy.ruffy.driver.IRuffyService;
import org.monkey.d.ruffy.ruffy.driver.display.Menu;
import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import de.jotomo.ruffyscripter.commands.Command;
import de.jotomo.ruffyscripter.commands.CommandException;
import de.jotomo.ruffyscripter.commands.CommandResult;
import de.jotomo.ruffyscripter.commands.ReadPumpStateCommand;

// TODO regularly read "My data" history (boluses, TBR) to double check all commands ran successfully.
// Automatically compare against AAPS db, or log all requests in the PumpInterface (maybe Milos
// already logs those requests somewhere ... and verify they have all been ack'd by the pump properly

/**
 * provides scripting 'runtime' and operations. consider moving operations into a separate
 * class and inject that into executing commands, so that commands operately solely on
 * operations and are cleanly separated from the thread management, connection management etc
 */
public class RuffyScripter {
    private static final Logger log = LoggerFactory.getLogger(RuffyScripter.class);


    private IRuffyService ruffyService;
    private final long connectionTimeOutMs = 5000;
    private String unrecoverableError = null;

    public volatile Menu currentMenu;
    private volatile long menuLastUpdated = 0;

    private volatile long lastCmdExecutionTime;
    private volatile Command activeCmd = null;

    private volatile boolean connected = false;
    private volatile long lastDisconnected = 0;

    private boolean started = false;

    private final Object keylock = new Object();
    private int keynotwait = 0;

    private final Object screenlock = new Object();

    public RuffyScripter() {

    }

    public void start(IRuffyService newService) {
        try {
            if(ruffyService!=null)
                try{ruffyService.removeHandler(mHandler);}catch(Exception e){};
            if(newService!=null) {
                this.ruffyService = newService;
                // TODO this'll be done better in v2 via ConnectionManager
                if (idleDisconnectMonitorThread.getState() == Thread.State.NEW) {
                    idleDisconnectMonitorThread.start();
                }
                started = true;
                try{newService.addHandler(mHandler);}catch (Exception e){}
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        if (started) {
            started=false;
            // TODO ruffy removes dead handlers automatically by now.
            // still, check this when going through recovery logic
/*            try {
                ruffyService.removeHandler(mHandler);
            } catch (RemoteException e) {
                log.warn("Removing IRTHandler from Ruffy service failed, ignoring", e);
            }*/
        }
    }

    public boolean isRunning() {
        return started;
    }

    private boolean canDisconnect = false;
    private Thread idleDisconnectMonitorThread = new Thread(new Runnable() {
        @Override
        public void run() {
            long lastDisconnect = System.currentTimeMillis();
            while (unrecoverableError == null) {
                try {
                    long now = System.currentTimeMillis();
                    if (connected && activeCmd == null
                            && now > lastCmdExecutionTime + connectionTimeOutMs
                            // don't disconnect too frequently, confuses ruffy?
                            && now > lastDisconnect + 15 * 1000) {
                        log.debug("Disconnecting after " + (connectionTimeOutMs / 1000) + "s inactivity timeout");
                        lastDisconnect = now;
                        canDisconnect=true;
                        ruffyService.doRTDisconnect(mHandler);
                        connected = false;
                        lastDisconnect = System.currentTimeMillis();
                        // don't attempt anything fancy in the next 10s, let the pump settle
                        SystemClock.sleep(10 * 1000);
                    }
                    else
                    {
                        canDisconnect=false;
                    }
                } catch (Exception e) {
                    // TODO do we need to catch this exception somewhere else too? right now it's
                    // converted into a command failure, but it's not classified as unrecoverable;
                    // eventually we might try to recover ... check docs, there's also another
                    // execption we should watch interacting with a remote service.
                    // SecurityException was the other, when there's an AIDL mismatch;
                    //unrecoverableError = "Ruffy service went away";
                    log.debug("Exception in idle disconnect monitor thread, carrying on", e);
                }
                SystemClock.sleep(1000);
            }
        }
    }, "idle-disconnect-monitor");

    private IRTHandler mHandler = new IRTHandler.Stub() {
        @Override
        public void log(String message) throws RemoteException {
            log.trace("Ruffy says: " + message);
        }

        @Override
        public void fail(String message) throws RemoteException {
            log.warn("Ruffy warns: " + message);
        }

        @Override
        public void requestBluetooth() throws RemoteException {
            log.trace("Ruffy invoked requestBluetooth callback");
        }

        @Override
        public boolean canDisconnect() throws RemoteException {
            return canDisconnect;
        }

        @Override
        public void rtStopped() throws RemoteException {
            log.debug("rtStopped callback invoked");
            currentMenu = null;
            connected = false;
        }

        @Override
        public void rtStarted() throws RemoteException {
            log.debug("rtStarted callback invoked");
            connected = true;
        }

        @Override
        public void rtClearDisplay() throws RemoteException {
        }

        @Override
        public void rtUpdateDisplay(byte[] quarter, int which) throws RemoteException {
        }

        @Override
        public void rtDisplayHandleMenu(Menu menu, int sequence) throws RemoteException {
            // method is called every ~500ms
            log.debug("rtDisplayHandleMenu: " + menu.getType());

            currentMenu = menu;
            menuLastUpdated = System.currentTimeMillis();

            synchronized (screenlock)
            {
                screenlock.notifyAll();
            }

            connected = true;

            // note that a WARNING_OR_ERROR menu can be a valid temporary state (cancelling TBR)
            // of a running command
            if (activeCmd == null && currentMenu.getType() == MenuType.WARNING_OR_ERROR) {
                log.warn("Warning/error menu encountered without a command running");
            }
        }

        @Override
        public void rtDisplayHandleNoMenu(int sequence) throws RemoteException {
            log.debug("rtDisplayHandleNoMenu callback invoked");
        }


        @Override
        public void keySent(int sequence) throws RemoteException {
            synchronized (keylock)
            {
                if(keynotwait>0)
                    keynotwait--;
                else
                    keylock.notify();
            }
        }

        @Override
        public String getServiceIdentifier() throws RemoteException {
            return this.toString();
        }

    };

    public boolean isPumpBusy() {
        return activeCmd != null;
    }

    public void unbind() {
        if(ruffyService!=null)
            try{ruffyService.removeHandler(mHandler);}catch (Exception e){}
        this.ruffyService = null;
    }

    private static class Returnable {
        CommandResult cmdResult;
    }

    /**
     * Always returns a CommandResult, never throws
     */
    public CommandResult runCommand(final Command cmd) {
        if (unrecoverableError != null) {
            return new CommandResult().success(false).enacted(false).message(unrecoverableError);
        }

        List<String> violations = cmd.validateArguments();
        if (!violations.isEmpty()) {
            return new CommandResult().message(Joiner.on("\n").join(violations)).state(readPumpState());
        }

        synchronized (RuffyScripter.class) {
            try {
                activeCmd = cmd;
                ensureConnected();
                final RuffyScripter scripter = this;
                final Returnable returnable = new Returnable();
                Thread cmdThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // check if pump is an an error state
                            if (currentMenu == null || currentMenu.getType() == MenuType.WARNING_OR_ERROR) {
                                try {
                                    returnable.cmdResult = new CommandResult().message("Pump is in an error state: " + currentMenu.getAttribute(MenuAttribute.MESSAGE));
                                    return;
                                } catch (Exception e) {
                                    returnable.cmdResult = new CommandResult().message("Pump is in an error state, reading the error state resulted in the attached exception").exception(e);
                                    return;
                                }
                            }
                            // Except for ReadPumpStateCommand: fail on all requests if the pump is suspended.
                            // All trickery of not executing but returning success, so that AAPS can non-sensically TBR away when suspended
                            // are dangerous in the current model where commands are dispatched without checking state beforehand, so
                            // the above tactic would result in boluses not being applied and no warning being raised.
                            // (Doing this check on the ComboPlugin level would require the plugin to fetch state from the pump,
                            //  deal with states changes (running/stopped), propagating that to AAPS and so on, adding more state,
                            //  which adds complexity I don't want in v1 and which requires more up-front design to do well,
                            //  esp. with AAPS).

                            // So, for v1, just check the pump is not suspended before executing commands and raising an error for all
                            // but the ReadPumpStateCommand. For v2, we'll have to come up with a better idea how to deal with the pump's
                            // state. Maybe having read-only commands and write/treatment commands treated differently, or maybe
                            // build an abstraction on top of the commands, so that e.g. a method on RuffyScripter encapsulates checking
                            // pre-condititions, running one or several commands, checking-post conditions and what not.
                            // Or maybe stick with commands, have them specify if they can run in stop mode. Think properly at which
                            // level to handle state and logic.
                            // For now, when changing cartridges and such: tell AAPS to stop the loop, change cartridge and resume the loop.
                            if (currentMenu == null || currentMenu.getType() == MenuType.STOP) {
                                if (cmd instanceof ReadPumpStateCommand) {
                                    returnable.cmdResult = new CommandResult().success(true).enacted(false);
                                } else {
                                    returnable.cmdResult = new CommandResult().success(false).enacted(false).message("Pump is suspended");
                                }
                                return;
                            }
                            log.debug("Connection ready to execute cmd " + cmd);
                            PumpState pumpState = readPumpState();
                            log.debug("Pump state before running command: " + pumpState);
                            long cmdStartTime = System.currentTimeMillis();
                            returnable.cmdResult = cmd.execute(scripter, pumpState);
                            long cmdEndTime = System.currentTimeMillis();
                            returnable.cmdResult.completionTime = cmdEndTime;
                            log.debug("Executing " + cmd + " took " + (cmdEndTime - cmdStartTime) + "ms");
                        } catch (CommandException e) {
                            returnable.cmdResult = e.toCommandResult();
                        } catch (Exception e) {
                            log.error("Unexpected exception running cmd", e);
                            returnable.cmdResult = new CommandResult().exception(e).message("Unexpected exception running cmd");
                        } finally {
                            lastCmdExecutionTime = System.currentTimeMillis();
                        }
                    }
                }, cmd.toString());
                cmdThread.start();

                // time out if nothing has been happening for more than 90s or after 4m
                // (to fail before the next loop iteration issues the next command)
                long dynamicTimeout = System.currentTimeMillis() + 90 * 1000;
                long overallTimeout = System.currentTimeMillis() + 4 * 60 * 1000;
                while (cmdThread.isAlive()) {
                    log.trace("Waiting for running command to complete");
                    SystemClock.sleep(500);
                    long now = System.currentTimeMillis();
                    if (now > dynamicTimeout) {
                        boolean menuRecentlyUpdated = now < menuLastUpdated + 5 * 1000;
                        boolean inMenuNotMainMenu = currentMenu != null && currentMenu.getType() != MenuType.MAIN_MENU;
                        if (menuRecentlyUpdated || inMenuNotMainMenu) {
                            // command still working (or waiting for pump to complete, e.g. bolus delivery)
                            dynamicTimeout = now + 30 * 1000;
                        } else {
                            log.error("Dynamic timeout running command " + activeCmd);
                            cmdThread.interrupt();
                            SystemClock.sleep(5000);
                            log.error("Timed out thread dead yet? " + cmdThread.isAlive());
                            return new CommandResult().success(false).enacted(false).message("Command stalled, check pump!");
                        }
                    }
                    if (now > overallTimeout) {
                        String msg = "Command " + cmd + " timed out after 4 min, check pump!";
                        log.error(msg);
                        return new CommandResult().success(false).enacted(false).message(msg);
                    }
                }

                if (returnable.cmdResult.state == null) {
                    returnable.cmdResult.state = readPumpState();
                }
                log.debug("Command result: " + returnable.cmdResult);
                return returnable.cmdResult;
            } catch (CommandException e) {
                return e.toCommandResult();
            } catch (Exception e) {
                log.error("Error in ruffyscripter/ruffy", e);
                return new CommandResult().exception(e).message("Unexpected exception communication with ruffy: " + e.getMessage());
            } finally {
                activeCmd = null;
            }
        }
    }

    /** If there's an issue, this times out eventually and throws a CommandException */
    private void ensureConnected() {
        try {
            boolean menuUpdateRecentlyReceived = currentMenu != null && menuLastUpdated + 1000 > System.currentTimeMillis();
            log.debug("ensureConnect, connected: " + connected + ", receiving menu updates: " + menuUpdateRecentlyReceived);
            if (menuUpdateRecentlyReceived) {
                log.debug("Pump is sending us menu updates, so we're connected");
                return;
            }

            // Occasionally the rtConnect is called a few seconds after the rtDisconnected
            // callback was called, in response to your disconnect request via doRtDisconnect.
            // When connecting again shortly after disconnecting, the pump sometimes fails
            // to come up. So for v1, just wait. This happens rarely, so no overly fancy logic needed.
            // TODO v2 see if we can do this cleaner, use isDisconnected as well maybe. GL#34.
            if (System.currentTimeMillis() < lastDisconnected + 10 * 1000) {
                log.debug("Waiting 10s to let pump settle after recent disconnect");
                SystemClock.sleep(10 * 1000);
            }

            canDisconnect=false;
            boolean connectInitSuccessful = ruffyService.doRTConnect(mHandler) == 0;
            log.debug("Connect init successful: " + connectInitSuccessful);
            log.debug("Waiting for first menu update to be sent");
            // Note: there was an 'if(currentMenu == null)' around the next call, since
            // the rtDisconnected callback sets currentMenu = null. However, there were
            // race conditions, so it was removed. And really, waiting for an update
            // to come in is a much safer bet.

            // waitForMenuUpdate times out after 60s and throws a CommandException.
            // if the user just pressed a button on the combo, the screen needs to time first
            // before a connection is possible. In that case, it takes 45s before the
            // connection comes up.
            waitForMenuUpdate(90);
        } catch (Exception e) {
            throw new CommandException().exception(e).message("Unexpected exception while initiating/restoring pump connection");
        }
    }

    // below: methods to be used by commands
    // TODO move into a new Operations(scripter) class commands can delegate to,
    // so this class can focus on providing a connection to run commands
    // (or maybe reconsider putting it into a base class)

    public static class Key {
        public static byte NO_KEY = (byte) 0x00;
        public static byte MENU = (byte) 0x03;
        public static byte CHECK = (byte) 0x0C;
        public static byte UP = (byte) 0x30;
        public static byte DOWN = (byte) 0xC0;
        public static byte BACK = (byte) 0x33;
    }

    public void pressUpKey() {
        log.debug("Pressing up key");
        pressKey(Key.UP,2000);
        log.debug("Releasing up key");
    }

    public void pressDownKey() {
        log.debug("Pressing down key");
        pressKey(Key.DOWN,2000);
        log.debug("Releasing down key");
    }

    public void pressCheckKey() {
        log.debug("Pressing check key");
        pressKey(Key.CHECK,2000);
        log.debug("Releasing check key");
    }

    public void pressMenuKey() {
        log.debug("Pressing menu key");
        pressKey(Key.MENU,2000);
        log.debug("Releasing menu key");
    }

    public void pressBackKey() {
        log.debug("Pressing back key");
        pressKey(Key.BACK,2000);
        log.debug("Releasing back key");
    }

    public boolean waitScreen(long timeout)
    {
        synchronized (screenlock) {
            try {
                screenlock.wait(timeout);
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    public boolean goToMainMenuScreen(MenuType screen, long timeout)
    {
        long start = System.currentTimeMillis();
        while((currentMenu == null || currentMenu.getType()!=screen) && start+timeout>System.currentTimeMillis())
        {
            if(currentMenu!=null && currentMenu.getType()==MenuType.WARNING_OR_ERROR)
            {
                //FIXME bad thing to do :D
                pressCheckKey();
            }
            else if(currentMenu!=null && !currentMenu.getType().isMaintype())
            {
                pressBackKey();
            }
            else
                pressMenuKey();
            waitScreen(250);
        }
        return currentMenu != null && currentMenu.getType()==screen;
    }

    public boolean enterMenu(MenuType startType, MenuType targetType, byte key, long timeout)
    {
        if(currentMenu.getType()==targetType)
            return true;
        if(currentMenu==null || currentMenu.getType() != startType)
            return false;
        long start = System.currentTimeMillis();
        pressKey(key,2000);
        while((currentMenu == null || currentMenu.getType()!=targetType) && start+timeout>System.currentTimeMillis()) {
            waitScreen(100);
        }
        return currentMenu!=null && currentMenu.getType()==targetType;
    }

    public void step(int steps, byte key, long timeout) {
        for(int i = 0; i < Math.abs(steps);i++)
            pressKey(key,timeout);
    }

    // TODO v2, rework these two methods: waitForMenuUpdate shoud only be used by commands
    // then anything longer than a few seconds is an error;
    // only ensureConnected() uses the method with the timeout parameter; inline that code,
    // so we can use a custom timeout and give a better error message in case of failure
    /**
     * Wait until the menu update is in
     */
    public void waitForMenuUpdate() {
       waitForMenuUpdate(60);
    }

    public void waitForMenuUpdate(long timeoutInSeconds) {
        long timeoutExpired = System.currentTimeMillis() + timeoutInSeconds * 1000;
        long initialUpdateTime = menuLastUpdated;
        while (initialUpdateTime == menuLastUpdated) {
            if (System.currentTimeMillis() > timeoutExpired) {
                throw new CommandException().message("Timeout waiting for menu update");
            }
            SystemClock.sleep(50);
        }
    }

    private void pressKey(final byte key, long timeout) {
        try {
            ruffyService.rtSendKey(key, true);
            //SystemClock.sleep(200);
            ruffyService.rtSendKey(Key.NO_KEY, true);
            if(timeout > 0)
            {
                synchronized (keylock)
                {
                    keylock.wait(timeout);
                }
            }
            else
            {
                synchronized (keylock)
                {
                    keynotwait++;
                }
            }
        } catch (Exception e) {
            throw new CommandException().exception(e).message("Error while pressing buttons");
        }
    }

    public void navigateToMenu(MenuType desiredMenu) {
        MenuType startedFrom = currentMenu.getType();
        boolean movedOnce = false;
        while (currentMenu.getType() != desiredMenu) {
            MenuType currentMenuType = currentMenu.getType();
            log.debug("Navigating to menu " + desiredMenu + ", currenty menu: " + currentMenuType);
            if (movedOnce && currentMenuType == startedFrom) {
                throw new CommandException().message("Menu not found searching for " + desiredMenu
                        + ". Check menu settings on your pump to ensure it's not hidden.");
            }
            pressMenuKey();
            waitForMenuToBeLeft(currentMenuType);
            movedOnce = true;
        }
    }

    /**
     * Wait till a menu changed has completed, "away" from the menu provided as argument.
     */
    public void waitForMenuToBeLeft(MenuType menuType) {
        long timeout = System.currentTimeMillis() + 60 * 1000;
        while (currentMenu.getType() == menuType) {
            if (System.currentTimeMillis() > timeout) {
                throw new CommandException().message("Timeout waiting for menu " + menuType + " to be left");
            }
            SystemClock.sleep(10);
        }
    }

    public void verifyMenuIsDisplayed(MenuType expectedMenu) {
        verifyMenuIsDisplayed(expectedMenu, null);
    }

    public void verifyMenuIsDisplayed(MenuType expectedMenu, String failureMessage) {
        int retries = 600;
        while (currentMenu.getType() != expectedMenu) {
            if (retries > 0) {
                SystemClock.sleep(100);
                retries = retries - 1;
            } else {
                if (failureMessage == null) {
                    failureMessage = "Invalid pump state, expected to be in menu " + expectedMenu + ", but current menu is " + currentMenu.getType();
                }
                throw new CommandException().message(failureMessage);
            }
        }
    }

    // TODO v2 add remaining info we can extract from the main menu, low battery and low
    // cartridge warnings, running extended bolus (how does that look if a TBR is active as well?)
    private PumpState readPumpState() {
        PumpState state = new PumpState();
        Menu menu = currentMenu;
        if (menu == null) {
            return new PumpState().errorMsg("Menu is not available");
        }
        MenuType menuType = menu.getType();
        if (menuType == MenuType.MAIN_MENU) {
            Double tbrPercentage = (Double) menu.getAttribute(MenuAttribute.TBR);
            if (tbrPercentage != 100) {
                state.tbrActive = true;
                Double displayedTbr = (Double) menu.getAttribute(MenuAttribute.TBR);
                state.tbrPercent = displayedTbr.intValue();
                MenuTime durationMenuTime = ((MenuTime) menu.getAttribute(MenuAttribute.RUNTIME));
                state.tbrRemainingDuration = durationMenuTime.getHour() * 60 + durationMenuTime.getMinute();
                state.tbrRate = ((double) menu.getAttribute(MenuAttribute.BASAL_RATE));
            }
            state.lowBattery = ((boolean) menu.getAttribute(MenuAttribute.LOW_BATTERY));
            state.insulinState = ((int) menu.getAttribute(MenuAttribute.INSULIN_STATE));
            // TODO v2, read current base basal rate, which is shown center when no TBR is active.
            // Check if that holds true when an extended bolus is running.
            // Add a field to PumpStatus, rather than renaming/overloading tbrRate to mean
            // either TBR rate or basal rate depending on whether a TBR is active.
        } else if (menuType == MenuType.WARNING_OR_ERROR) {
            state.errorMsg = (String) menu.getAttribute(MenuAttribute.MESSAGE);
        } else if (menuType == MenuType.STOP) {
            state.suspended = true;
            state.lowBattery = ((boolean) menu.getAttribute(MenuAttribute.LOW_BATTERY));
            state.insulinState = ((int) menu.getAttribute(MenuAttribute.INSULIN_STATE));
        } else {
            StringBuilder sb = new StringBuilder();
            for (MenuAttribute menuAttribute : menu.attributes()) {
                sb.append(menuAttribute);
                sb.append(": ");
                sb.append(menu.getAttribute(menuAttribute));
                sb.append("\n");
            }
            state.errorMsg = "Pump is on menu " + menuType + ", listing attributes: \n" + sb.toString();
        }
        return state;
    }
}
