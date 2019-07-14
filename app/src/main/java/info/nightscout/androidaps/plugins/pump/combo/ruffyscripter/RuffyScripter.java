package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Joiner;

import org.monkey.d.ruffy.ruffy.driver.IRTHandler;
import org.monkey.d.ruffy.ruffy.driver.IRuffyService;
import org.monkey.d.ruffy.ruffy.driver.display.Menu;
import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.BolusType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.ReadQuickInfoCommand;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.PumpHistoryRequest;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.BolusCommand;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.CancelTbrCommand;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.Command;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.CommandException;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.ConfirmAlertCommand;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.ReadBasalProfileCommand;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.ReadHistoryCommand;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.ReadPumpStateCommand;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.SetBasalProfileCommand;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands.SetTbrCommand;

/**
 * Provides scripting 'runtime' and operations. consider moving operations into a separate
 * class and inject that into executing commands, so that commands operately solely on
 * operations and are cleanly separated from the thread management, connection management etc
 */
public class RuffyScripter implements RuffyCommands {
    private static final Logger log = LoggerFactory.getLogger(RuffyScripter.class);

    private IRuffyService ruffyService;

    @Nullable
    private volatile Menu currentMenu;
    private volatile long menuLastUpdated = 0;
    private volatile boolean unparsableMenuEncountered;

    private volatile Command activeCmd = null;

    private boolean started = false;

    private final Object screenlock = new Object();

    private IRTHandler mHandler = new IRTHandler.Stub() {
        @Override
        public void log(String message) {
            if (log.isTraceEnabled()) {
                log.trace("Ruffy says: " + message);
            }
        }

        @Override
        public void fail(String message) {
            log.warn("Ruffy warns: " + message);
        }

        @Override
        public void requestBluetooth() {
            log.trace("Ruffy invoked requestBluetooth callback");
        }

        @Override
        public void rtStopped() {
            log.debug("rtStopped callback invoked");
            currentMenu = null;
        }

        @Override
        public void rtStarted() {
            log.debug("rtStarted callback invoked");
        }

        @Override
        public void rtClearDisplay() {
        }

        @Override
        public void rtUpdateDisplay(byte[] quarter, int which) {
        }

        @Override
        public void rtDisplayHandleMenu(Menu menu) {
            // method is called every ~500ms
            log.debug("rtDisplayHandleMenu: " + menu);

            currentMenu = menu;
            menuLastUpdated = System.currentTimeMillis();

            synchronized (screenlock) {
                screenlock.notifyAll();
            }
        }

        @Override
        public void rtDisplayHandleNoMenu() {
            log.warn("rtDisplayHandleNoMenu callback invoked");
            unparsableMenuEncountered = true;
        }
    };

    public RuffyScripter(Context context) {
        boolean boundSucceeded = false;

        try {
            Intent intent = new Intent()
                    .setComponent(new ComponentName(
                            // this must be the base package of the app (check package attribute in
                            // manifest element in the manifest file of the providing app)
                            "org.monkey.d.ruffy.ruffy",
                            // full path to the driver;
                            // in the logs this service is mentioned as (note the slash)
                            // "org.monkey.d.ruffy.ruffy/.driver.Ruffy";
                            // org.monkey.d.ruffy.ruffy is the base package identifier
                            // and /.driver.Ruffy the service within the package
                            "org.monkey.d.ruffy.ruffy.driver.Ruffy"
                    ));
            context.startService(intent);

            ServiceConnection mRuffyServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    log.debug("ruffy service connected");
                    ruffyService = IRuffyService.Stub.asInterface(service);
                    try {
                        ruffyService.setHandler(mHandler);
                    } catch (Exception e) {
                        log.error("Ruffy handler has issues", e);
                    }
                    started = true;
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    log.debug("ruffy service disconnected");
                }
            };
            boundSucceeded = context.bindService(intent, mRuffyServiceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            log.error("Binding to ruffy service failed", e);
        }

        if (!boundSucceeded) {
            log.error("No connection to ruffy. Pump control unavailable.");
        }
    }

    @Override
    public boolean isPumpAvailable() {
        return started;
    }

    @Override
    public boolean isPumpBusy() {
        return activeCmd != null;
    }

    @Override
    public boolean isConnected() {
        if (ruffyService == null) {
            return false;
        }
        try {
            if (!ruffyService.isConnected()) {
                return false;
            }
            return ruffyService.isConnected() && System.currentTimeMillis() - menuLastUpdated < 10 * 1000;
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public synchronized void disconnect() {
        if (ruffyService == null) {
            return;
        }
        try {
            log.debug("Disconnecting");
            ruffyService.doRTDisconnect();
        } catch (RemoteException e) {
            // ignore
        } catch (Exception e) {
            log.warn("Disconnect not happy", e);
        }
    }

    @Override
    public CommandResult readPumpState() {
        return runCommand(new ReadPumpStateCommand());
    }

    @Override
    public CommandResult readQuickInfo(int numberOfBolusRecordsToRetrieve) {
        return runCommand(new ReadQuickInfoCommand(numberOfBolusRecordsToRetrieve));
    }

    public void returnToRootMenu() {
        // returning to main menu using the 'back' key does not cause a vibration
        MenuType menuType = getCurrentMenu().getType();
        while (menuType != MenuType.MAIN_MENU && menuType != MenuType.STOP && menuType != MenuType.WARNING_OR_ERROR) {
            log.debug("Going back to main menu, currently at " + menuType);
            pressBackKey();
            while (getCurrentMenu().getType() == menuType) {
                waitForScreenUpdate();
            }
            menuType = getCurrentMenu().getType();
        }
    }

    /**
     * Always returns a CommandResult, never throws
     */
    private CommandResult runCommand(final Command cmd) {
        log.debug("Attempting to run cmd: " + cmd);

        List<String> violations = cmd.validateArguments();
        if (!violations.isEmpty()) {
            log.error("Command argument violations: " + Joiner.on(", ").join(violations));
            return new CommandResult().success(false).state(new PumpState());
        }

        synchronized (RuffyScripter.class) {
            Thread cmdThread = null;
            try {
                activeCmd = cmd;
                unparsableMenuEncountered = false;
                long connectStart = System.currentTimeMillis();
                ensureConnected();
                log.debug("Connection ready to execute cmd " + cmd);
                cmdThread = new Thread(() -> {
                    try {
                        if (!runPreCommandChecks(cmd)) {
                            return;
                        }
                        PumpState pumpState = readPumpStateInternal();
                        log.debug("Pump state before running command: " + pumpState);

                        // execute the command
                        cmd.setScripter(RuffyScripter.this);
                        long cmdStartTime = System.currentTimeMillis();
                        cmd.execute();
                        long cmdEndTime = System.currentTimeMillis();
                        log.debug("Executing " + cmd + " took " + (cmdEndTime - cmdStartTime) + "ms");
                    } catch (CommandException e) {
                        log.error("CommandException running command", e);
                        cmd.getResult().success = false;
                    } catch (Exception e) {
                        log.error("Unexpected exception running cmd", e);
                        cmd.getResult().success = false;
                    }
                }, cmd.getClass().getSimpleName());
                long executionStart = System.currentTimeMillis();
                cmdThread.start();

                long overallTimeout = System.currentTimeMillis() + 10 * 60 * 1000;
                while (cmdThread.isAlive()) {
                    if (!isConnected()) {
                        // on connection loss try to reconnect, confirm warning alerts caused by
                        // the disconnected and then return the command as failed (the caller
                        // can retry if needed).
                        log.debug("Connection unusable (ruffy connection: " + ruffyService.isConnected() + ", "
                                + "time since last menu update: " + (System.currentTimeMillis() - menuLastUpdated) + " ms, "
                                + "aborting command and attempting reconnect ...");
                        cmdThread.interrupt();
                        activeCmd.getResult().success = false;

                        // the BT connection might be still there, but we might not be receiving
                        // menu updates, so force a disconnect before connecting again
                        disconnect();
                        SystemClock.sleep(500);
                        for (int attempts = 2; attempts > 0; attempts--) {
                            boolean reconnected = recoverFromConnectionLoss();
                            if (reconnected) {
                                break;
                            }
                            // connect attempt times out after 90s, shortly wait and then retry;
                            // (90s timeout + 5s wait) * 2 attempts = 190s
                            SystemClock.sleep(5 * 1000);
                        }
                        break;
                    }

                    if (System.currentTimeMillis() > overallTimeout) {
                        log.error("Command " + cmd + " timed out");
                        cmdThread.interrupt();
                        activeCmd.getResult().success = false;
                        break;
                    }

                    if (unparsableMenuEncountered) {
                        log.error("UnparsableMenuEncountered flagged, aborting command");
                        cmdThread.interrupt();
                        activeCmd.getResult().invalidSetup = true;
                        activeCmd.getResult().success = false;
                    }

                    log.trace("Waiting for running command to complete");
                    SystemClock.sleep(500);
                }

                activeCmd.getResult().state = readPumpStateInternal();
                CommandResult result = activeCmd.getResult();
                if (log.isDebugEnabled()) {
                    long connectDurationSec = (executionStart - connectStart) / 1000;
                    long executionDurationSec = (System.currentTimeMillis() - executionStart) / 1000;
                    log.debug("Command result: " + result);
                    log.debug("Connect: " + connectDurationSec + "s, execution: " + executionDurationSec + "s");
                }
                return result;
            } catch (CommandException e) {
                log.error("CommandException while executing command", e);
                PumpState pumpState = recoverFromCommandFailure();
                return activeCmd.getResult().success(false).state(pumpState);
            } catch (Exception e) {
                log.error("Unexpected exception communication with ruffy", e);
                PumpState pumpState = recoverFromCommandFailure();
                return activeCmd.getResult().success(false).state(pumpState);
            } finally {
                Menu menu = this.currentMenu;
                if (activeCmd.getResult().success && menu != null && menu.getType() != MenuType.MAIN_MENU) {
                    log.warn("Command " + activeCmd + " successful, but finished leaving pump on menu " + getCurrentMenuName());
                }
                if (cmdThread != null) {
                    try {
                        // let command thread finish updating activeCmd var
                        cmdThread.join(1000);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                activeCmd = null;
            }
        }
    }

    private boolean runPreCommandChecks(Command cmd) {
        if (cmd instanceof ReadPumpStateCommand) {
            // always allowed, state is set at the end of runCommand method
            activeCmd.getResult().success = true;
        } else if (getCurrentMenu().getType() == MenuType.STOP) {
            if (cmd.needsRunMode()) {
                log.error("Requested command requires run mode, but pump is suspended");
                activeCmd.getResult().success = false;
                return false;
            }
        } else if (getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
            if (!(cmd instanceof ConfirmAlertCommand)) {
                log.warn("Warning/alert active on pump, but requested command is not ConfirmAlertCommand");
                activeCmd.getResult().success = false;
                return false;
            }
        } else if (getCurrentMenu().getType() != MenuType.MAIN_MENU) {
            log.debug("Pump is unexpectedly not on main menu but " + getCurrentMenuName() + ", trying to recover");
            try {
                recoverFromCommandFailure();
            } catch (Exception e) {
                activeCmd.getResult().success = false;
                return false;
            }
            if (getCurrentMenu().getType() != MenuType.MAIN_MENU) {
                activeCmd.getResult().success = false;
                return false;
            }
        }
        return true;
    }

    /**
     * On connection loss the pump raises an alert immediately (when setting a TBR or giving a bolus) -
     * there's no timeout before that happens. But: a reconnect is still possible which can then
     * confirm the alert.
     *
     * @return whether the reconnect and return to main menu was successful
     */
    private boolean recoverFromConnectionLoss() {
        log.debug("Connection was lost, trying to reconnect");
        ensureConnected();
        if (getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
            WarningOrErrorCode warningOrErrorCode = readWarningOrErrorCode();
            if (Objects.equals(activeCmd.getReconnectWarningId(), warningOrErrorCode.warningCode)) {
                log.debug("Confirming warning caused by disconnect: #" + warningOrErrorCode.warningCode);
                // confirm alert
                verifyMenuIsDisplayed(MenuType.WARNING_OR_ERROR);
                pressCheckKey();
                // dismiss alert
                verifyMenuIsDisplayed(MenuType.WARNING_OR_ERROR);
                pressCheckKey();
            }
        }

        boolean connected = isConnected();
        if (connected) {
            MenuType menuType = getCurrentMenu().getType();
            if (menuType != MenuType.MAIN_MENU && menuType != MenuType.WARNING_OR_ERROR) {
                returnToRootMenu();
            }
        }
        log.debug("Recovery from connection loss " + (connected ? "succeeded" : "failed"));
        return connected;
    }

    /**
     * Returns to the main menu (if possible) after a command failure, so that subsequent commands
     * reusing the connection won't fail and returns the current PumpState (empty if unreadable).
     */
    private PumpState recoverFromCommandFailure() {
        Menu menu = this.currentMenu;
        if (menu == null) {
            return new PumpState();
        }
        MenuType type = menu.getType();
        if (type != MenuType.WARNING_OR_ERROR && type != MenuType.MAIN_MENU) {
            try {
                log.debug("Command execution yielded an error, returning to main menu");
                returnToRootMenu();
            } catch (Exception e) {
                log.warn("Error returning to main menu, when trying to recover from command failure", e);
            }
        }
        try {
            PumpState pumpState = readPumpStateInternal();
            return pumpState;
        } catch (Exception e) {
            log.debug("Reading pump state during recovery failed", e);
            return new PumpState();
        }
    }

    /**
     * If there's an issue, this times out eventually and throws a CommandException
     */
    private void ensureConnected() {
        try {
            if (isConnected()) {
                return;
            }

            boolean connectInitSuccessful = ruffyService.doRTConnect() == 0;
            log.debug("Connect init successful: " + connectInitSuccessful);
            log.debug("Waiting for first menu update to be sent");
            long timeoutExpired = System.currentTimeMillis() + 90 * 1000;
            long initialUpdateTime = menuLastUpdated;
            while (initialUpdateTime == menuLastUpdated) {
                if (System.currentTimeMillis() > timeoutExpired) {
                    throw new CommandException("Timeout connecting to pump");
                }
                SystemClock.sleep(50);
            }
        } catch (CommandException e) {
            try {
                ruffyService.doRTDisconnect();
            } catch (RemoteException e1) {
                log.warn("Disconnect after connect failure failed", e1);
            }
            throw e;
        } catch (Exception e) {
            try {
                ruffyService.doRTDisconnect();
            } catch (RemoteException e1) {
                log.warn("Disconnect after connect failure failed", e1);
            }
            throw new CommandException("Unexpected exception while initiating/restoring pump connection", e);
        }
    }

    /**
     * This reads the state of the pump, which is whatever is currently displayed on the display,
     * no actions are performed.
     */
    public PumpState readPumpStateInternal() {
        PumpState state = new PumpState();
        state.timestamp = System.currentTimeMillis();
        Menu menu = currentMenu;
        if (menu == null) {
            log.debug("Returning empty PumpState, menu is unavailable");
            return state;
        }

        log.debug("Parsing menu: " + menu);
        MenuType menuType = menu.getType();
        state.menu = menuType.name();

        if (menuType == MenuType.MAIN_MENU) {
            Double tbrPercentage = (Double) menu.getAttribute(MenuAttribute.TBR);
            BolusType bolusType = (BolusType) menu.getAttribute(MenuAttribute.BOLUS_TYPE);
            Integer activeBasalRate = (Integer) menu.getAttribute(MenuAttribute.BASAL_SELECTED);

            if (!activeBasalRate.equals(1)) {
                state.unsafeUsageDetected = PumpState.UNSUPPORTED_BASAL_RATE_PROFILE;
            } else if (bolusType != null && bolusType != BolusType.NORMAL) {
                state.unsafeUsageDetected = PumpState.UNSUPPORTED_BOLUS_TYPE;
            } else if (tbrPercentage != null && tbrPercentage != 100) {
                state.tbrActive = true;
                Double displayedTbr = (Double) menu.getAttribute(MenuAttribute.TBR);
                state.tbrPercent = displayedTbr.intValue();
                MenuTime durationMenuTime = ((MenuTime) menu.getAttribute(MenuAttribute.RUNTIME));
                state.tbrRemainingDuration = durationMenuTime.getHour() * 60 + durationMenuTime.getMinute();
            }
            if (menu.attributes().contains(MenuAttribute.BASAL_RATE)) {
                state.basalRate = ((double) menu.getAttribute(MenuAttribute.BASAL_RATE));
            }
            if (menu.attributes().contains(MenuAttribute.BATTERY_STATE)) {
                state.batteryState = ((int) menu.getAttribute(MenuAttribute.BATTERY_STATE));
            }
            if (menu.attributes().contains(MenuAttribute.INSULIN_STATE)) {
                state.insulinState = ((int) menu.getAttribute(MenuAttribute.INSULIN_STATE));
            }
            if (menu.attributes().contains(MenuAttribute.TIME)) {
                MenuTime pumpTime = (MenuTime) menu.getAttribute(MenuAttribute.TIME);
                Date date = new Date();
                // infer yesterday as the pump's date if midnight just passed, but the pump is
                // a bit behind
                if (date.getHours() == 0 && date.getMinutes() <= 5
                        && pumpTime.getHour() == 23 && pumpTime.getMinute() >= 55) {
                    date.setTime(date.getTime() - 24 * 60 * 60 * 1000);
                }
                date.setHours(pumpTime.getHour());
                date.setMinutes(pumpTime.getMinute());
                date.setSeconds(0);
                state.pumpTime = date.getTime() - date.getTime() % 1000;
            }
        } else if (menuType == MenuType.WARNING_OR_ERROR) {
            state.activeAlert = readWarningOrErrorCode();
        } else if (menuType == MenuType.STOP) {
            state.suspended = true;
            if (menu.attributes().contains(MenuAttribute.BATTERY_STATE)) {
                state.batteryState = ((int) menu.getAttribute(MenuAttribute.BATTERY_STATE));
            }
            if (menu.attributes().contains(MenuAttribute.INSULIN_STATE)) {
                state.insulinState = ((int) menu.getAttribute(MenuAttribute.INSULIN_STATE));
            }
            if (menu.attributes().contains(MenuAttribute.TIME)) {
                MenuTime time = (MenuTime) menu.getAttribute(MenuAttribute.TIME);
                Date date = new Date();
                date.setHours(time.getHour());
                date.setMinutes(time.getMinute());
                date.setSeconds(0);
                state.pumpTime = date.getTime() - date.getTime() % 1000;
            }
        }

        log.debug("State read: " + state);
        return state;
    }

    @NonNull
    public WarningOrErrorCode readWarningOrErrorCode() {
        if (currentMenu == null || getCurrentMenu().getType() != MenuType.WARNING_OR_ERROR) {
            return new WarningOrErrorCode(null, null, null);
        }
        Integer warningCode = (Integer) getCurrentMenu().getAttribute(MenuAttribute.WARNING);
        Integer errorCode = (Integer) getCurrentMenu().getAttribute(MenuAttribute.ERROR);
        int retries = 5;
        while (warningCode == null && errorCode == null && retries > 0) {
            waitForScreenUpdate();
            warningCode = (Integer) getCurrentMenu().getAttribute(MenuAttribute.WARNING);
            errorCode = (Integer) getCurrentMenu().getAttribute(MenuAttribute.ERROR);
            retries--;
        }
        String message = (String) getCurrentMenu().getAttribute(MenuAttribute.MESSAGE);
        return new WarningOrErrorCode(warningCode, errorCode, message);
    }

    public static class Key {
        public static byte NO_KEY = (byte) 0x00;
        public static byte MENU = (byte) 0x03;
        public static byte CHECK = (byte) 0x0C;
        public static byte UP = (byte) 0x30;
        public static byte DOWN = (byte) 0xC0;
        public static byte BACK = (byte) 0x33;
    }

    // === pump ops ===
    @NonNull
    public Menu getCurrentMenu() {
        if (Thread.currentThread().isInterrupted())
            throw new CommandException("Interrupted");
        Menu menu = this.currentMenu;
        if (menu == null) {
            log.error("currentMenu == null, bailing");
            throw new CommandException("Unable to read current menu");
        }
        return menu;
    }

    @Nullable
    private String getCurrentMenuName() {
        Menu menu = this.currentMenu;
        return menu != null ? menu.getType().toString() : "<none>";
    }

    public void pressUpKey() {
        log.debug("Pressing up key");
        pressKey(Key.UP);
        log.debug("Releasing up key");
    }

    public void pressDownKey() {
        log.debug("Pressing down key");
        pressKey(Key.DOWN);
        log.debug("Releasing down key");
    }

    public void pressCheckKey() {
        log.debug("Pressing check key");
        pressKey(Key.CHECK);
        log.debug("Releasing check key");
    }

    public void pressMenuKey() {
        log.debug("Pressing menu key");
        pressKey(Key.MENU);
        log.debug("Releasing menu key");
    }

    private void pressBackKey() {
        log.debug("Pressing back key");
        pressKey(Key.BACK);
        log.debug("Releasing back key");
    }

    public void pressKeyMs(final byte key, long ms) {
        long stepMs = 100;
        try {
            log.debug("Scroll: Pressing key for " + ms + " ms with step " + stepMs + " ms");
            ruffyService.rtSendKey(key, true);
            ruffyService.rtSendKey(key, false);
            while (ms > stepMs) {
                SystemClock.sleep(stepMs);
                ruffyService.rtSendKey(key, false);
                ms -= stepMs;
            }
            SystemClock.sleep(ms);
            ruffyService.rtSendKey(Key.NO_KEY, true);
            log.debug("Releasing key");
        } catch (Exception e) {
            throw new CommandException("Error while pressing buttons");
        }
    }

    /**
     * Wait until the menu is updated
     */
    public void waitForScreenUpdate() {
        if (Thread.currentThread().isInterrupted())
            throw new CommandException("Interrupted");
        synchronized (screenlock) {
            try {
                // updates usually come in every ~500, occasionally up to 1100ms
                screenlock.wait((long) 2000);
            } catch (InterruptedException e) {
                throw new CommandException("Interrupted");
            }
        }
    }

    private void pressKey(final byte key) {
        if (Thread.currentThread().isInterrupted())
            throw new CommandException("Interrupted");
        try {
            ruffyService.rtSendKey(key, true);
            SystemClock.sleep(150);
            ruffyService.rtSendKey(Key.NO_KEY, true);
        } catch (Exception e) {
            throw new CommandException("Error while pressing buttons");
        }
    }

    public void navigateToMenu(MenuType desiredMenu) {
        verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        int moves = 20;
        MenuType lastSeenMenu = getCurrentMenu().getType();
        while (lastSeenMenu != desiredMenu) {
            log.debug("Navigating to menu " + desiredMenu + ", current menu: " + lastSeenMenu);
            moves--;
            if (moves == 0) {
                throw new CommandException("Menu not found searching for " + desiredMenu
                        + ". Check menu settings on your pump to ensure it's not hidden.");
            }
            MenuType next = getCurrentMenu().getType();
            pressMenuKey();
            // sometimes the pump takes a bit longer (more than one screen refresh) to advance
            // to the next menu. wait until we actually see the change to avoid overshoots.
            while (next == lastSeenMenu) {
                waitForScreenUpdate();
                next = getCurrentMenu().getType();
            }
            lastSeenMenu = getCurrentMenu().getType();
        }
    }

    /**
     * Wait till a menu changed has completed, "away" from the menu provided as argument.
     */
    public void waitForMenuToBeLeft(MenuType menuType) {
        long timeout = System.currentTimeMillis() + 10 * 1000;
        while (getCurrentMenu().getType() == menuType) {
            if (System.currentTimeMillis() > timeout) {
                throw new CommandException("Timeout waiting for menu " + menuType + " to be left");
            }
            waitForScreenUpdate();
        }
    }

    public void verifyMenuIsDisplayed(MenuType expectedMenu) {
        verifyMenuIsDisplayed(expectedMenu, null);
    }

    public void verifyMenuIsDisplayed(MenuType expectedMenu, String failureMessage) {
        int attempts = 5;
        while (getCurrentMenu().getType() != expectedMenu) {
            attempts -= 1;
            if (attempts > 0) {
                waitForScreenUpdate();
            } else {
                if (failureMessage == null) {
                    failureMessage = "Invalid pump state, expected to be in menu " + expectedMenu + ", but current menu is " + getCurrentMenuName();
                }
                throw new CommandException(failureMessage);
            }
        }
    }

    public void verifyRootMenuIsDisplayed() {
        int retries = 600;
        while (getCurrentMenu().getType() != MenuType.MAIN_MENU && getCurrentMenu().getType() != MenuType.STOP) {
            if (retries > 0) {
                SystemClock.sleep(100);
                retries = retries - 1;
            } else {
                throw new CommandException("Invalid pump state, expected to be in menu MAIN or STOP but current menu is " + getCurrentMenuName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T readBlinkingValue(Class<T> expectedType, MenuAttribute attribute) {
        int retries = 5;
        Object value = getCurrentMenu().getAttribute(attribute);
        while (!expectedType.isInstance(value)) {
            value = getCurrentMenu().getAttribute(attribute);
            waitForScreenUpdate();
            retries--;
            if (retries == 0) {
                throw new CommandException("Failed to read blinkng value: " + attribute + "=" + value + " type=" + value);
            }
        }
        return (T) value;
    }

    @Override
    public CommandResult deliverBolus(double amount, BolusProgressReporter bolusProgressReporter) {
        return runCommand(new BolusCommand(amount, bolusProgressReporter));
    }

    @Override
    public void cancelBolus() {
        if (activeCmd instanceof BolusCommand) {
            ((BolusCommand) activeCmd).requestCancellation();
        } else {
            log.error("cancelBolus called, but active command is not a bolus:" + activeCmd);
        }
    }

    @Override
    public CommandResult setTbr(int percent, int duration) {
        return runCommand(new SetTbrCommand(percent, duration));
    }

    @Override
    public CommandResult cancelTbr() {
        return runCommand(new CancelTbrCommand());
    }

    @Override
    public CommandResult confirmAlert(int warningCode) {
        return runCommand(new ConfirmAlertCommand(warningCode));
    }

    @Override
    public CommandResult readHistory(PumpHistoryRequest request) {
        return runCommand(new ReadHistoryCommand(request));
    }

    @Override
    public CommandResult readBasalProfile() {
        return runCommand(new ReadBasalProfileCommand());
    }

    @Override
    public CommandResult setBasalProfile(BasalProfile basalProfile) {
        return runCommand(new SetBasalProfileCommand(basalProfile));
    }

    @Override
    public CommandResult getDateAndTime() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public CommandResult setDateAndTime() {
        throw new RuntimeException("Not supported");
    }

    /**
     * Confirms and dismisses the given alert if it's raised before the timeout
     */
    public boolean confirmAlert(@NonNull Integer warningCode, int maxWaitMs) {
        long timeout = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < timeout) {
            if (getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                WarningOrErrorCode warningOrErrorCode = readWarningOrErrorCode();
                if (warningOrErrorCode.errorCode != null) {
                    throw new CommandException("Pump is in error state");
                }
                Integer displayedWarningCode = warningOrErrorCode.warningCode;
                String errorMsg = null;
                try {
                    errorMsg = (String) getCurrentMenu().getAttribute(MenuAttribute.MESSAGE);
                } catch (Exception e) {
                    // ignore
                }
                if (!Objects.equals(displayedWarningCode, warningCode)) {
                    throw new CommandException("An alert other than the expected warning " + warningCode + " was raised by the pump: "
                            + displayedWarningCode + "(" + errorMsg + "). Please check the pump.");
                }

                // confirm alert
                verifyMenuIsDisplayed(MenuType.WARNING_OR_ERROR);
                pressCheckKey();
                // dismiss alert
                // if the user has confirmed the alert we have dismissed it with the button press
                // above already, so only do that if an alert is still displayed
                waitForScreenUpdate();
                if (getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                    pressCheckKey();
                }
                // wait till the pump has processed the alarm, otherwise it might still be showing
                // when a command returns
                WarningOrErrorCode displayedWarning = readWarningOrErrorCode();
                while (Objects.equals(displayedWarning.warningCode, warningCode)) {
                   waitForScreenUpdate();
                   displayedWarning = readWarningOrErrorCode();
                }
                return true;
            }
            SystemClock.sleep(10);
        }
        return false;
    }
}
