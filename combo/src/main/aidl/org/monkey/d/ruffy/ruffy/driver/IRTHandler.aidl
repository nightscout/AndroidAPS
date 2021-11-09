// IRTHandler.aidl
package org.monkey.d.ruffy.ruffy.driver;

// Declare any non-default types here with import statements
import org.monkey.d.ruffy.ruffy.driver.display.Menu;

interface IRTHandler {
    void log(String message);
    void fail(String message);

    void requestBluetooth();
    void rtStopped();
    void rtStarted();

    void rtClearDisplay();
    void rtUpdateDisplay(in byte[] quarter, int which);

    void rtDisplayHandleMenu(in Menu menu);
    void rtDisplayHandleNoMenu();
}
