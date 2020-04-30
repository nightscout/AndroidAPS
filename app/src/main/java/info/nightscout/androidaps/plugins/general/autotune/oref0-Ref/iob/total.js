function iobTotal(opts, time) {

    var now = time.getTime();
    var iobCalc = opts.calculate;
    var treatments = opts.treatments;
    var profile_data = opts.profile;
    var dia = profile_data.dia;
    var peak = 0;
    var iob = 0;
    var basaliob = 0;
    var bolusiob = 0;
    var netbasalinsulin = 0;
    var bolusinsulin = 0;
    //var bolussnooze = 0;
    var activity = 0;
    if (!treatments) return {};
    //if (typeof time === 'undefined') {
        //var time = new Date();
    //}

    // force minimum DIA of 3h
    if (dia < 3) {
        //console.error("Warning; adjusting DIA from",dia,"to minimum of 3 hours");
        dia = 3;
    }

    var curveDefaults = {
        'bilinear': {
            requireLongDia: false,
            peak: 75 // not really used, but prevents having to check later
        },
        'rapid-acting': {
            requireLongDia: true,
            peak: 75,
            tdMin: 300
        },
        'ultra-rapid': {
            requireLongDia: true,
            peak: 55,
            tdMin: 300
        },
    };

    var curve = 'bilinear';

    if (profile_data.curve !== undefined) {
        curve = profile_data.curve.toLowerCase();
    }

    if (!(curve in curveDefaults)) {
        console.error('Unsupported curve function: "' + curve + '". Supported curves: "bilinear", "rapid-acting" (Novolog, Novorapid, Humalog, Apidra) and "ultra-rapid" (Fiasp). Defaulting to "rapid-acting".');
        curve = 'rapid-acting';
    }

    var defaults = curveDefaults[curve];

    // Force minimum of 5 hour DIA when default requires a Long DIA.
    if (defaults.requireLongDia && dia < 5) {
        //console.error('Pump DIA must be set to 5 hours or more with the new curves, please adjust your pump. Defaulting to 5 hour DIA.');
        dia = 5;
    }

    peak = defaults.peak;

    treatments.forEach(function(treatment) {
        if( treatment.date <= now ) {
            var dia_ago = now - dia*60*60*1000;
            if( treatment.date > dia_ago ) {
                // tIOB = total IOB
                var tIOB = iobCalc(treatment, time, curve, dia, peak, profile_data);
                if (tIOB && tIOB.iobContrib) { iob += tIOB.iobContrib; }
                if (tIOB && tIOB.activityContrib) { activity += tIOB.activityContrib; }
                // basals look like either of these:
                // {"insulin":-0.05,"date":1507265512363.6365,"created_at":"2017-10-06T04:51:52.363Z"}
                // {"insulin":0.05,"date":1507266530000,"created_at":"2017-10-06T05:08:50.000Z"}
                // boluses look like:
                // {"timestamp":"2017-10-05T22:06:31-07:00","started_at":"2017-10-06T05:06:31.000Z","date":1507266391000,"insulin":0.5}
                if (treatment.insulin && tIOB && tIOB.iobContrib) {
                    if (treatment.insulin < 0.1) {
                        basaliob += tIOB.iobContrib;
                        netbasalinsulin += treatment.insulin;
                    } else {
                        bolusiob += tIOB.iobContrib;
                        bolusinsulin += treatment.insulin;
                    }
                }
                //console.error(JSON.stringify(treatment));
            }
        } // else { console.error("ignoring future treatment:",treatment); }
    });

    return {
        iob: Math.round(iob * 1000) / 1000,
        activity: Math.round(activity * 10000) / 10000,
        basaliob: Math.round(basaliob * 1000) / 1000,
        bolusiob: Math.round(bolusiob * 1000) / 1000,
        netbasalinsulin: Math.round(netbasalinsulin * 1000) / 1000,
        bolusinsulin: Math.round(bolusinsulin * 1000) / 1000,
        time: time
    };
}

exports = module.exports = iobTotal;
