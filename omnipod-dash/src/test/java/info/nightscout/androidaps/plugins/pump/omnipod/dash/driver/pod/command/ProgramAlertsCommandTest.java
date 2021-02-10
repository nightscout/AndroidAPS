package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

public class ProgramAlertsCommandTest {
    // TODO

    // Address for all captures below: 37879811

    /*
    Pod expiration alerts

    V/PodComm: *** encode() CALLED ON ProgramAlertsCommandEncoder
    *** encode() CALLED ON HeaderEncoder
    V/PodComm: *** encode() RESULT FOR HeaderEncoder: 02:42:00:03:8C:12 FROM HeaderEncoder{lengthSequenceNumberAndFlags=-29678, f6666a=[], encoded=[2, 66, 0, 3, -116, 18], headerEncoder=null, commandId=0, f6671f=0, commandBodyLength=0}
    *** encode() RESULT FOR ProgramAlertsCommandEncoder: 02:42:00:03:8C:12:19:10:49:4E:53:2E:79:A4:10:D1:05:02:28:00:12:75:06:02:80:F5 FROM ProgramAlertsCommandEncoder{parameters=ProgramAlertsCommandParameters{configurations=[AlertConfigurationEncoder{slot=7, enabled=true, durationInMinutes=420, autoOff=false, timeTrigger=false, offsetInMinutes=4305, beepType=2, beepRepetition=5}, AlertConfigurationEncoder{slot=2, enabled=true, durationInMinutes=0, autoOff=false, timeTrigger=false, offsetInMinutes=4725, beepType=2, beepRepetition=6}]}, f6666a=[], encoded=[2, 66, 0, 3, -116, 18, 25, 16, 73, 78, 83, 46, 121, -92, 16, -47, 5, 2, 40, 0, 18, 117, 6, 2, -128, -11], headerEncoder=HeaderEncoder{lengthSequenceNumberAndFlags=-29678, f6666a=[], encoded=[2, 66, 0, 3, -116, 18], headerEncoder=null, commandId=0, f6671f=0, commandBodyLength=0}, commandId=25, f6671f=0, commandBodyLength=16}

    I/PodComm: pod command: 024200038C121910494E532E79A410D1050228001275060280F5
    V/PodComm: flags: seqNum=3 ack=false mctf=true
     Program Alert:
       length:16, number of alerts:2
     -------------------------------------
       alert index: 7 (lump of coal/pod expiration)
       enabled: true
       duration: 420 minutes
       set alarm: false
    V/PodComm:    type: time - trigger after 4305 minutes (71.75 hrs)
     beep type: 2
     beep repetition: 5
     -------------------------------------
       alert index: 2 (imminent pod expiration)
       enabled: true
       duration: 0 minutes
       set alarm: false
       type: time - trigger after 4725 minutes (78.75 hrs)
     beep type: 2
     beep repetition: 6

     */

        /*
    Low reservoir

    V/PodComm: *** encode() RESULT FOR HeaderEncoder: 02:42:00:03:20:0C FROM HeaderEncoder{lengthSequenceNumberAndFlags=8204, f6666a=[], encoded=[2, 66, 0, 3, 32, 12], headerEncoder=null, commandId=0, f6671f=0, commandBodyLength=0}
    V/PodComm: *** encode() RESULT FOR ProgramAlertsCommandEncoder: 02:42:00:03:20:0C:19:0A:49:4E:53:2E:4C:00:00:C8:01:02:01:49 FROM ProgramAlertsCommandEncoder{parameters=ProgamAlertsCommandParameters{configurations=[AlertConfigurationEncoder{slot=4, enabled=true, durationInMinutes=0, autoOff=false, timeTrigger=true, offsetInMinutes=200, beepType=2, beepRepetition=1}]}, f6666a=[], encoded=[2, 66, 0, 3, 32, 12, 25, 10, 73, 78, 83, 46, 76, 0, 0, -56, 1, 2, 1, 73], headerEncoder=HeaderEncoder{lengthSequenceNumberAndFlags=8204, f6666a=[], encoded=[2, 66, 0, 3, 32, 12], headerEncoder=null, commandId=0, f6671f=0, commandBodyLength=0}, commandId=25, f6671f=0, commandBodyLength=10}

    V/PodComm: flags: seqNum=8 ack=false mctf=false
     Program Alert:
       length:10, number of alerts:1
     -------------------------------------
       alert index: 4 (low reservoir)
       enabled: true
       duration: 0 minutes
       set alarm: false
       type: volume - trigger at 200 micro liter
     beep type: 2
     beep repetition: 1
     */

    /*
    User Pod expiration

    V/PodComm: *** encode() CALLED ON ProgramAlertsCommandEncoder
    *** encode() CALLED ON HeaderEncoder
    V/PodComm: *** encode() RESULT FOR HeaderEncoder: 02:42:00:03:3C:0C FROM HeaderEncoder{lengthSequenceNumberAndFlags=15372, f6666a=[], encoded=[2, 66, 0, 3, 60, 12], headerEncoder=null, commandId=0, f6671f=0, commandBodyLength=0}
    V/PodComm: *** encode() RESULT FOR ProgramAlertsCommandEncoder: 02:42:00:03:3C:0C:19:0A:49:4E:53:2E:38:00:0F:EF:03:02:03:E2 FROM ProgramAlertsCommandEncoder{parameters=ProgramAlertsCommandParameters{configurations=[AlertConfigurationEncoder{slot=3, enabled=true, durationInMinutes=0, autoOff=false, timeTrigger=false, offsetInMinutes=4079, beepType=2, beepRepetition=3}]}, f6666a=[], encoded=[2, 66, 0, 3, 60, 12, 25, 10, 73, 78, 83, 46, 56, 0, 15, -17, 3, 2, 3, -30], headerEncoder=HeaderEncoder{lengthSequenceNumberAndFlags=15372, f6666a=[], encoded=[2, 66, 0, 3, 60, 12], headerEncoder=null, commandId=0, f6671f=0, commandBodyLength=0}, commandId=25, f6671f=0, commandBodyLength=10}

    I/PodComm: pod command: 024200033C0C190A494E532E38000FEF030203E2
    V/PodComm: flags: seqNum=15 ack=false mctf=false
     Program Alert:
       length:10, number of alerts:1
     -------------------------------------
       alert index: 3 (user pod expiration)
       enabled: true
       duration: 0 minutes
       set alarm: false
       type: time - trigger after 4079 minutes (67.98 hrs)
     beep type: 2
     beep repetition: 3
     */


    /*
    Lump of coal

    V/PodComm: *** encode() CALLED ON ProgramAlertsCommandEncoder
    *** encode() CALLED ON HeaderEncoder
    *** encode() RESULT FOR HeaderEncoder: 02:42:00:03:28:0C FROM HeaderEncoder{lengthSequenceNumberAndFlags=10252, f6666a=[], encoded=[2, 66, 0, 3, 40, 12], headerEncoder=null, commandId=0, f6671f=0, commandBodyLength=0}
    D/MainActivity: Pod Activation 1: got Pod Event
    V/PodComm: *** encode() RESULT FOR ProgramAlertsCommandEncoder: 02:42:00:03:28:0C:19:0A:49:4E:53:2E:78:37:00:05:08:02:03:56 FROM ProgramAlertsCommandEncoder{parameters=ProgramAlertsCommandParameters{configurations=[AlertConfigurationEncoder{slot=7, enabled=true, durationInMinutes=55, autoOff=false, timeTrigger=false, offsetInMinutes=5, beepType=2, beepRepetition=8}]}, f6666a=[], encoded=[2, 66, 0, 3, 40, 12, 25, 10, 73, 78, 83, 46, 120, 55, 0, 5, 8, 2, 3, 86], headerEncoder=HeaderEncoder{lengthSequenceNumberAndFlags=10252, f6666a=[], encoded=[2, 66, 0, 3, 40, 12], headerEncoder=null, commandId=0, f6671f=0, commandBodyLength=0}, commandId=25, f6671f=0, commandBodyLength=10}

    V/PodComm: flags: seqNum=10 ack=false mctf=false
     Program Alert:
       length:10, number of alerts:1
     -------------------------------------
      V/PodComm:    alert index: 7 (lump of coal/pod expiration)
       enabled: true
       duration: 55 minutes
       set alarm: false
       type: time - trigger after 5 minutes (0.08 hrs)
     beep type: 2
     beep repetition: 8
     */
}
