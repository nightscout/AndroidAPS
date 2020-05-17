#!/usr/bin/env node

/*
  oref0 autotuning data prep tool

  Collects and divides up glucose data for periods dominated by carb absorption,
  correction insulin, or basal insulin, and adds in avgDelta and deviations,
  for use in oref0 autotuning algorithm

  Released under MIT license. See the accompanying LICENSE.txt file for
  full terms and conditions

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
  THE SOFTWARE.

*/

var generate = require('../lib/autotune-prep');
var _ = require('lodash');
var moment = require('moment');

if (!module.parent) {

    var argv = require('yargs')
        .usage("$0 <pumphistory.json> <profile.json> <glucose.json> <pumpprofile.json> [<carbhistory.json>] [--categorize_uam_as_basal] [--tune-insulin-curve]")
        .option('categorize_uam_as_basal', {
            alias: 'u',
            boolean: true,
            describe: "Categorize UAM as basal",
            default: false
        })
        .option('tune-insulin-curve', {
            alias: 'i',
            boolean: true,
            describe: "Tune peak time and end time",
            default: false
        })
        .strict(true)
        .help('help');

    var params = argv.argv;
    var inputs = params._;

    if (inputs.length < 4 || inputs.length > 5) {
        argv.showHelp();
        console.log('{ "error": "Insufficient arguments" }');
        process.exit(1);
    }

    var pumphistory_input = inputs[0];
    var profile_input = inputs[1];
    var glucose_input = inputs[2];
    var pumpprofile_input = inputs[3];
    var carb_input = inputs[4];

    var fs = require('fs');
    try {
        var pumphistory_data = JSON.parse(fs.readFileSync(pumphistory_input, 'utf8'));
        var profile_data = JSON.parse(fs.readFileSync(profile_input, 'utf8'));
    } catch (e) {
        console.log('{ "error": "Could not parse input data" }');
        return console.error("Could not parse input data: ", e);
    }

    var pumpprofile_data = { };
    if (typeof pumpprofile_input !== 'undefined') {
        try {
            pumpprofile_data = JSON.parse(fs.readFileSync(pumpprofile_input, 'utf8'));
        } catch (e) {
            console.error("Warning: could not parse "+pumpprofile_input);
        }
    }

    // disallow impossibly low carbRatios due to bad decoding
    if ( typeof(profile_data.carb_ratio) === 'undefined' || profile_data.carb_ratio < 2 ) {
        if ( typeof(pumpprofile_data.carb_ratio) === 'undefined' || pumpprofile_data.carb_ratio < 2 ) {
            console.log('{ "carbs": 0, "mealCOB": 0, "reason": "carb_ratios ' + profile_data.carb_ratio + ' and ' + pumpprofile_data.carb_ratio + ' out of bounds" }');
            return console.error("Error: carb_ratios " + profile_data.carb_ratio + ' and ' + pumpprofile_data.carb_ratio + " out of bounds");
        } else {
            profile_data.carb_ratio = pumpprofile_data.carb_ratio;
        }
    }

    // get insulin curve from pump profile that is maintained
    profile_data.curve = pumpprofile_data.curve;

    // Pump profile has an up to date copy of useCustomPeakTime from preferences
    // If the preferences file has useCustomPeakTime use the previous autotune dia and PeakTime.
    // Otherwise, use data from pump profile.
    if (!pumpprofile_data.useCustomPeakTime) {
      profile_data.dia = pumpprofile_data.dia;
      profile_data.insulinPeakTime = pumpprofile_data.insulinPeakTime;
    }

    // Always keep the curve value up to date with what's in the user preferences
    profile_data.curve = pumpprofile_data.curve;

    try {
        var glucose_data = JSON.parse(fs.readFileSync(glucose_input, 'utf8'));
    } catch (e) {
        console.error("Warning: could not parse "+glucose_input);
    }

    var carb_data = { };
    if (typeof carb_input !== 'undefined') {
        try {
            carb_data = JSON.parse(fs.readFileSync(carb_input, 'utf8'));
        } catch (e) {
            console.error("Warning: could not parse "+carb_input);
        }
    }

    // Have to sort history - NS sort doesn't account for different zulu and local timestamps
    pumphistory_data = _.orderBy(pumphistory_data, [function (o) { return moment(o.created_at).valueOf(); }], ['desc']);

    inputs = {
      history: pumphistory_data
    , profile: profile_data
    , pumpprofile: pumpprofile_data
    , carbs: carb_data
    , glucose: glucose_data
    , categorize_uam_as_basal: params.categorize_uam_as_basal
    , tune_insulin_curve: params['tune-insulin-curve']
    };

    var prepped_glucose = generate(inputs);
    console.log(JSON.stringify(prepped_glucose));
}

