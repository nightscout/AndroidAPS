// IRuffyService.aidl
package org.monkey.d.ruffy.ruffy.driver;

// Declare any non-default types here with import statements
import org.monkey.d.ruffy.ruffy.driver.IRTHandler;

interface IRuffyService {

    void setHandler(IRTHandler handler);

    /** Connect to the pump
    *
    * @return 0 if successful, -1 otherwise
    */
    int doRTConnect();

    /** Disconnect from the pump */
    void doRTDisconnect();

    void rtSendKey(byte keyCode, boolean changed);
    void resetPairing();
    boolean isConnected();
}
