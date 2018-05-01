package com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages;

/**
 * Created by geoff on 6/2/16.
 */
public class ButtonPressCarelinkMessageBody extends CarelinkLongMessageBody {
    public static final byte BUTTON_EASY = 0x00;
    public static final byte BUTTON_ESC = 0x01;
    public static final byte BUTTON_ACT = 0x02;
    public static final byte BUTTON_UP = 0x03;
    public static final byte BUTTON_DOWN = 0x04;

    public ButtonPressCarelinkMessageBody(int which) {
        init(which);
    }

    public void init(int buttonType) {
        int numArgs = 1;
        super.init(new byte[] {(byte)numArgs,(byte)buttonType});
    }


}
