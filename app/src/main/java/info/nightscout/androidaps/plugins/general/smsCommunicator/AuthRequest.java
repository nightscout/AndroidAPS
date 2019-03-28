package info.nightscout.androidaps.plugins.general.smsCommunicator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.DateUtil;

class AuthRequest {
    private static Logger log = LoggerFactory.getLogger(L.SMS);

    Sms requester;
    String confirmCode;
    private Runnable action;

    private long date;

    private boolean processed;
    private SmsCommunicatorPlugin plugin;

    AuthRequest(SmsCommunicatorPlugin plugin, Sms requester, String requestText, String confirmCode, SmsAction action) {
        this.requester = requester;
        this.confirmCode = confirmCode;
        this.action = action;
        this.plugin = plugin;

        this.date = DateUtil.now();

        plugin.sendSMS(new Sms(requester.phoneNumber, requestText));
    }

    void action(String codeReceived) {
        if (processed) {
            if (L.isEnabled(L.SMS))
                log.debug("Already processed");
            return;
        }
        if (!confirmCode.equals(codeReceived)) {
            processed = true;
            if (L.isEnabled(L.SMS))
                log.debug("Wrong code");
            plugin.sendSMS(new Sms(requester.phoneNumber, R.string.sms_wrongcode));
            return;
        }
        if (DateUtil.now() - date < Constants.SMS_CONFIRM_TIMEOUT) {
            processed = true;
            if (L.isEnabled(L.SMS))
                log.debug("Processing confirmed SMS: " + requester.text);
            if (action != null)
                action.run();
            return;
        }
        if (L.isEnabled(L.SMS))
            log.debug("Timed out SMS: " + requester.text);
    }

}
