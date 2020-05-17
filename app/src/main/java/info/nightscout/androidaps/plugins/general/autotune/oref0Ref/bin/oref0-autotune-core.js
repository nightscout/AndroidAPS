#!/usr/bin/env node

/*
  oref0 autotuning tool

  Uses the output of oref0-autotune-prep.js

  Calculates adjustments to basal schedule, ISF, and CSF 

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

var autotune = require('../lib/autotune');
var stringify = require('json-stable-stringify');

if (!module.parent) {
    var argv = require('yargs')
        .usage("$0 <autotune/glucose.json> <autotune/autotune.json> <settings/profile.json>")
        .demand(3)
        .strict(true)
        .help('help');

    var params = argv.argv;
    var inputs = params._;

    var prepped_glucose_input = inputs[0];
    var previous_autotune_input = inputs[1];
    var pumpprofile_input = inputs[2];

    var fs = require('fs');
    try {
        var prepped_glucose_data = JSON.parse(fs.readFileSync(prepped_glucose_input, 'utf8'));
        var previous_autotune_data = JSON.parse(fs.readFileSync(previous_autotune_input, 'utf8'));
        var pumpprofile_data = JSON.parse(fs.readFileSync(pumpprofile_input, 'utf8'));
    } catch (e) {
        console.log('{ "error": "Could not parse input data" }');
        return console.error("Could not parse input data: ", e);
    }

    // Pump profile has an up to date copy of useCustomPeakTime from preferences
    // If the preferences file has useCustomPeakTime use the previous autotune dia and PeakTime.
    // Otherwise, use data from pump profile.
    if (!pumpprofile_data.useCustomPeakTime) {
      previous_autotune_data.dia = pumpprofile_data.dia;
      previous_autotune_data.insulinPeakTime = pumpprofile_data.insulinPeakTime;
    }

    // Always keep the curve value up to date with what's in the user preferences
    previous_autotune_data.curve = pumpprofile_data.curve;

    inputs = {
        preppedGlucose: prepped_glucose_data
      , previousAutotune: previous_autotune_data
      , pumpProfile: pumpprofile_data
    };

    var autotune_output = autotune(inputs);
    console.log(stringify(autotune_output, { space: '   '}));
}

