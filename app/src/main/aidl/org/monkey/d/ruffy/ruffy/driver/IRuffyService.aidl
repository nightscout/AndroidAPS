// IRuffyService.aidl
package org.monkey.d.ruffy.ruffy.driver;

// Declare any non-default types here with import statements
import org.monkey.d.ruffy.ruffy.driver.IRTHandler;

interface IRuffyService {

    void setHandler(IRTHandler handler);
    int doRTConnect();
    void doRTDisconnect();
    void rtSendKey(byte keyCode, boolean changed);
    void resetPairing();
}
