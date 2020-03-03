package info.nightscout.androidaps.plugins.aps.loop;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

/*
{
        "_id": "576cfd15217b0bed77d63641",
        "device": "openaps://indy2",
        "pump": {
            "battery": {
            "status": "normal",
            "voltage": 1.56
        },
        "status": {
            "status": "normal",
            "timestamp": "2016-06-24T09:26:38.000Z",
            "bolusing": false,
            "suspended": false
            },
            "reservoir": 31.25,
            "clock": "2016-06-24T02:26:16-07:00"
        },
        "openaps": {
            "suggested": {
            "bg": 173,
            "temp": "absolute",
            "snoozeBG": 194,
            "timestamp": "2016-06-24T09:27:40.000Z",
            "predBGs": {
            "IOB": [173, 178, 183, 187, 191, 194, 196, 197, 198, 197, 197, 195, 192, 190, 187, 184, 181, 178, 175, 172, 169, 167, 164, 162, 160, 158, 156, 154, 152, 151, 149, 148, 147, 146, 146, 145]
            },
            "reason": "COB: 0, Dev: 46, BGI: -1.92, ISF: 80, Target: 115; Eventual BG 194>=115, adj. req. rate:2.7 to maxSafeBasal:2.3, temp 2.25 >~ req 2.3U/hr",
            "COB": 0,
            "eventualBG": 194,
            "tick": "+6",
            "IOB": 0.309
            },
            "iob": [{
            "netbasalinsulin": -0.3,
            "activity": 0.0048,
            "basaliob": 0.078,
            "time": "2016-06-24T09:26:16.000Z",
            "hightempinsulin": 0.25,
            "bolussnooze": 0,
            "iob": 0.309
            }, {
            "netbasalinsulin": -0.15,
            "activity": 0.0041,
            "basaliob": 0.238,
            "time": "2016-06-24T09:31:16.000Z",
            "hightempinsulin": 0.4,
            "bolussnooze": 0,
            "iob": 0.438
            }, {
            "netbasalinsulin": 0,
            "activity": 0.0036,
            "basaliob": 0.345,
            "time": "2016-06-24T09:36:16.000Z",
            "hightempinsulin": 0.5,
            "bolussnooze": 0,
            "iob": 0.52
            }, {
            "netbasalinsulin": 0.2,
            "activity": 0.0036,
            "basaliob": 0.5,
            "time": "2016-06-24T09:41:16.000Z",
            "hightempinsulin": 0.65,
            "bolussnooze": 0,
            "iob": 0.653
            }, {
            "netbasalinsulin": 0.35,
            "activity": 0.0038,
            "basaliob": 0.602,
            "time": "2016-06-24T09:46:16.000Z",
            "hightempinsulin": 0.75,
            "bolussnooze": 0,
            "iob": 0.734
            }, {
            "netbasalinsulin": 0.45,
            "activity": 0.0042,
            "basaliob": 0.651,
            "time": "2016-06-24T09:51:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.763
            }, {
            "netbasalinsulin": 0.45,
            "activity": 0.0045,
            "basaliob": 0.647,
            "time": "2016-06-24T09:56:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.74
            }, {
            "netbasalinsulin": 0.5,
            "activity": 0.0048,
            "basaliob": 0.639,
            "time": "2016-06-24T10:01:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.716
            }, {
            "netbasalinsulin": 0.5,
            "activity": 0.0052,
            "basaliob": 0.628,
            "time": "2016-06-24T10:06:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.691
            }, {
            "netbasalinsulin": 0.5,
            "activity": 0.0055,
            "basaliob": 0.614,
            "time": "2016-06-24T10:11:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.663
            }, {
            "netbasalinsulin": 0.5,
            "activity": 0.0059,
            "basaliob": 0.596,
            "time": "2016-06-24T10:16:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.633
            }, {
            "netbasalinsulin": 0.55,
            "activity": 0.0063,
            "basaliob": 0.575,
            "time": "2016-06-24T10:21:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.602
            }, {
            "netbasalinsulin": 0.55,
            "activity": 0.0067,
            "basaliob": 0.549,
            "time": "2016-06-24T10:26:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.568
            }, {
            "netbasalinsulin": 0.55,
            "activity": 0.0071,
            "basaliob": 0.521,
            "time": "2016-06-24T10:31:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.533
            }, {
            "netbasalinsulin": 0.6,
            "activity": 0.0074,
            "basaliob": 0.489,
            "time": "2016-06-24T10:36:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.496
            }, {
            "netbasalinsulin": 0.6,
            "activity": 0.0075,
            "basaliob": 0.456,
            "time": "2016-06-24T10:41:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.458
            }, {
            "netbasalinsulin": 0.6,
            "activity": 0.0075,
            "basaliob": 0.42,
            "time": "2016-06-24T10:46:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.421
            }, {
            "netbasalinsulin": 0.6,
            "activity": 0.0073,
            "basaliob": 0.384,
            "time": "2016-06-24T10:51:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.384
            }, {
            "netbasalinsulin": 0.65,
            "activity": 0.0071,
            "basaliob": 0.349,
            "time": "2016-06-24T10:56:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.349
            }, {
            "netbasalinsulin": 0.65,
            "activity": 0.0069,
            "basaliob": 0.314,
            "time": "2016-06-24T11:01:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.314
            }, {
            "netbasalinsulin": 0.65,
            "activity": 0.0066,
            "basaliob": 0.281,
            "time": "2016-06-24T11:06:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.281
            }, {
            "netbasalinsulin": 0.65,
            "activity": 0.0062,
            "basaliob": 0.25,
            "time": "2016-06-24T11:11:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.25
            }, {
            "netbasalinsulin": 0.65,
            "activity": 0.0059,
            "basaliob": 0.221,
            "time": "2016-06-24T11:16:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.221
            }, {
            "netbasalinsulin": 0.65,
            "activity": 0.0055,
            "basaliob": 0.193,
            "time": "2016-06-24T11:21:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.193
            }, {
            "netbasalinsulin": 0.65,
            "activity": 0.0052,
            "basaliob": 0.167,
            "time": "2016-06-24T11:26:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.167
            }, {
            "netbasalinsulin": 0.7,
            "activity": 0.0049,
            "basaliob": 0.143,
            "time": "2016-06-24T11:31:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.143
            }, {
            "netbasalinsulin": 0.7,
            "activity": 0.0045,
            "basaliob": 0.12,
            "time": "2016-06-24T11:36:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.12
            }, {
            "netbasalinsulin": 0.7,
            "activity": 0.0041,
            "basaliob": 0.1,
            "time": "2016-06-24T11:41:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.1
            }, {
            "netbasalinsulin": 0.7,
            "activity": 0.0037,
            "basaliob": 0.081,
            "time": "2016-06-24T11:46:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.081
            }, {
            "netbasalinsulin": 0.75,
            "activity": 0.0034,
            "basaliob": 0.064,
            "time": "2016-06-24T11:51:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.064
            }, {
            "netbasalinsulin": 0.75,
            "activity": 0.003,
            "basaliob": 0.049,
            "time": "2016-06-24T11:56:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.049
            }, {
            "netbasalinsulin": 0.8,
            "activity": 0.0026,
            "basaliob": 0.036,
            "time": "2016-06-24T12:01:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.036
            }, {
            "netbasalinsulin": 0.8,
            "activity": 0.0021,
            "basaliob": 0.026,
            "time": "2016-06-24T12:06:16.000Z",
            "hightempinsulin": 0.8,
            "bolussnooze": 0,
            "iob": 0.026
            }, {
            "netbasalinsulin": 0.75,
            "activity": 0.0017,
            "basaliob": 0.017,
            "time": "2016-06-24T12:11:16.000Z",
            "hightempinsulin": 0.75,
            "bolussnooze": 0,
            "iob": 0.017
            }, {
            "netbasalinsulin": 0.75,
            "activity": 0.0013,
            "basaliob": 0.011,
            "time": "2016-06-24T12:16:16.000Z",
            "hightempinsulin": 0.75,
            "bolussnooze": 0,
            "iob": 0.011
            }, {
            "netbasalinsulin": 0.65,
            "activity": 0.0009,
            "basaliob": 0.006,
            "time": "2016-06-24T12:21:16.000Z",
            "hightempinsulin": 0.65,
            "bolussnooze": 0,
            "iob": 0.006
            }],
            "enacted": {
            "bg": 161,
            "temp": "absolute",
            "snoozeBG": 181,
            "recieved": true,
            "predBGs": {
            "IOB": [161, 164, 166, 168, 170, 172, 174, 175, 176, 177, 177, 176, 175, 175, 174, 173, 173, 172, 172, 171, 171, 171, 171, 170, 170, 170, 170, 170, 169, 169, 169, 169, 169, 168]
            },
            "reason": "COB: undefined, Dev: 33, BGI: -2.56, ISF: 80, Target: 115; Eventual BG 181>=115, adj. req. rate:2.4 to maxSafeBasal:2.3, temp 1<2.3U/hr",
            "rate": 2.25,
            "eventualBG": 181,
            "timestamp": "2016-06-24T09:19:06.000Z",
            "duration": 30,
            "tick": "+5",
            "IOB": 0.166
            }
        },
        "mmtune": {
            "scanDetails": [
            ["916.564", 5, -78],
            ["916.588", 3, -80],
            ["916.612", 4, -68],
            ["916.636", 5, -65],
            ["916.660", 5, -60],
            ["916.684", 5, -67],
            ["916.708", 5, -71]
            ],
            "setFreq": 916.66,
            "timestamp": "2016-06-24T09:26:22.000Z",
            "usedDefault": false
        },
        "created_at": "2016-06-24T09:27:49.230Z"
        }
*/

public class DeviceStatus {
    private static Logger log = LoggerFactory.getLogger(L.APS);

    public String device = null;
    public JSONObject pump = null;
    public JSONObject enacted = null;
    public JSONObject suggested = null;
    public JSONObject iob = null;
    public int uploaderBattery = 0;
    public String created_at = null;

    public JSONObject mongoRecord() {
        JSONObject record = new JSONObject();

        try {
            if (device != null) record.put("device", device);
            if (pump != null) record.put("pump", pump);
            JSONObject openaps = new JSONObject();
            if (enacted != null) openaps.put("enacted", enacted);
            if (suggested != null) openaps.put("suggested", suggested);
            if (iob != null) openaps.put("iob", iob);
            record.put("openaps", openaps);
            if (uploaderBattery != 0) record.put("uploaderBattery", uploaderBattery);
            if (created_at != null) record.put("created_at", created_at);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return record;
    }

}
